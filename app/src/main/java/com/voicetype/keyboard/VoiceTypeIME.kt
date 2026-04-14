package com.voicetype.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File

/**
 * VoiceTypeIME - InputMethodService principal
 * 
 * Pipeline:
 * 1. Usuario pulsa 🎤 → graba audio
 * 2. Suelta botón → para grabación
 * 3. Whisper.cpp → transcribe (ES/CA auto-detect)
 * 4. LLM (Phi-3) → limpia y mejora texto
 * 5. commitText() → inserta resultado
 * 
 * Arquitectura Híbrida:
 * - XML Views para el teclado (máximo rendimiento, baja latencia)
 * - Jetpack Compose para Settings (UI moderna y mantenible)
 */
class VoiceTypeIME : InputMethodService() {

    private var keyboardView: View? = null
    
    // UI Components - Barra de estado
    private var statusBar: LinearLayout? = null
    private var statusIcon: ImageView? = null
    private var statusText: TextView? = null
    private var processingIndicator: ProgressBar? = null
    
    // UI Components - Botones principales
    private var micContainer: FrameLayout? = null
    private var micButton: ImageView? = null
    private var btnSpace: Button? = null
    private var btnDelete: ImageButton? = null
    
    // UI Components - Fila inferior
    private var btnSettings: ImageButton? = null
    private var btnLanguage: Button? = null
    private var btnEnter: ImageButton? = null
    
    private val audioRecorder = AudioRecorder()
    private val whisperEngine = WhisperEngine()
    private val llmEngine = LLMEngine()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var isRecording = false
    private var tempAudioFile: File? = null
    private var currentLanguage = Language.AUTO

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.voice_keyboard_view, null)
        
        // Inicializar componentes - Barra de estado
        statusBar = keyboardView?.findViewById(R.id.status_bar)
        statusIcon = keyboardView?.findViewById(R.id.status_icon)
        statusText = keyboardView?.findViewById(R.id.status_text)
        processingIndicator = keyboardView?.findViewById(R.id.processing_indicator)
        
        // Inicializar componentes - Botones principales
        micContainer = keyboardView?.findViewById(R.id.btn_mic_container)
        micButton = keyboardView?.findViewById(R.id.btn_mic)
        btnSpace = keyboardView?.findViewById(R.id.btn_space)
        btnDelete = keyboardView?.findViewById(R.id.btn_delete)
        
        // Inicializar componentes - Fila inferior
        btnSettings = keyboardView?.findViewById(R.id.btn_settings)
        btnLanguage = keyboardView?.findViewById(R.id.btn_language)
        btnEnter = keyboardView?.findViewById(R.id.btn_enter)
        
        setupMicButton()
        setupOtherButtons()
        updateLanguageButton()
        
        return keyboardView!!
    }

    private fun setupMicButton() {
        micContainer?.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupOtherButtons() {
        // Botón Espacio
        btnSpace?.setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
            performVibration()
        }
        
        // Botón Borrar
        btnDelete?.setOnLongClickListener {
            // Borrar todo el texto
            currentInputConnection?.deleteSurroundingText(Integer.MAX_VALUE, Integer.MAX_VALUE)
            performVibration()
            true
        }
        btnDelete?.setOnClickListener {
            // Borrar un carácter
            currentInputConnection?.deleteSurroundingText(1, 0)
            performVibration()
        }
        
        // Botón Ajustes (abre Settings en Compose)
        btnSettings?.setOnClickListener {
            openSettingsActivity()
        }
        
        // Botón Idioma
        btnLanguage?.setOnClickListener {
            cycleLanguage()
            performVibration()
        }
        
        // Botón Enter
        btnEnter?.setOnClickListener {
            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            performVibration()
        }
    }

    private fun openSettingsActivity() {
        val intent = android.content.Intent(this, ui.SettingsActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun cycleLanguage() {
        currentLanguage = when (currentLanguage) {
            Language.AUTO -> Language.SPANISH
            Language.SPANISH -> Language.CATALAN
            Language.CATALAN -> Language.AUTO
        }
        updateLanguageButton()
        Toast.makeText(this, getLanguageDisplayName(), Toast.LENGTH_SHORT).show()
    }

    private fun updateLanguageButton() {
        btnLanguage?.text = when (currentLanguage) {
            Language.AUTO -> "AUTO"
            Language.SPANISH -> "ES"
            Language.CATALAN -> "CA"
        }
    }

    private fun getLanguageDisplayName(): String {
        return when (currentLanguage) {
            Language.AUTO -> "Detección automática"
            Language.SPANISH -> "Español"
            Language.CATALAN -> "Català"
        }
    }

    private fun startRecording() {
        if (!audioRecorder.hasPermission(this)) {
            Toast.makeText(this, R.string.error_audio_permission, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            tempAudioFile = File(cacheDir, "recording_${System.currentTimeMillis()}.wav")
            audioRecorder.startRecording(tempAudioFile!!)
            isRecording = true
            
            updateUIState(UIState.RECORDING)
            
            // Vibración al iniciar grabación
            performVibration()
            
        } catch (e: Exception) {
            showError(R.string.error_recording)
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        
        try {
            audioRecorder.stopRecording()
            isRecording = false
            
            updateUIState(UIState.PROCESSING)
            
            // Procesar en background
            processAudio()
            
        } catch (e: Exception) {
            showError(R.string.error_recording)
            e.printStackTrace()
            updateUIState(UIState.IDLE)
        }
    }

    private fun processAudio() {
        scope.launch {
            try {
                val languageCode = when (currentLanguage) {
                    Language.AUTO -> "auto"
                    Language.SPANISH -> "es"
                    Language.CATALAN -> "ca"
                }
                
                // Paso 1: Transcripción con Whisper
                val transcription = withContext(Dispatchers.IO) {
                    whisperEngine.transcribe(tempAudioFile!!, language = languageCode)
                }
                
                mainHandler.post {
                    statusText?.text = "Transcrito: $transcription"
                }
                
                // Paso 2: Mejora con LLM
                val improvedText = withContext(Dispatchers.IO) {
                    llmEngine.improveText(transcription)
                }
                
                // Paso 3: Insertar texto
                mainHandler.post {
                    currentInputConnection?.commitText(improvedText, 1)
                    updateUIState(UIState.IDLE)
                    hideStatusBar()
                    
                    // Limpiar archivo temporal
                    tempAudioFile?.delete()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    showError(R.string.error_transcription)
                    updateUIState(UIState.IDLE)
                }
            }
        }
    }

    private fun updateUIState(state: UIState) {
        mainHandler.post {
            when (state) {
                UIState.IDLE -> {
                    micContainer?.isEnabled = true
                    micButton?.alpha = 1.0f
                    statusIcon?.setColorFilter(ContextCompat.getColor(this, R.color.mic_idle))
                    hideStatusBar()
                }
                UIState.RECORDING -> {
                    showStatusBar()
                    statusIcon?.setColorFilter(ContextCompat.getColor(this, R.color.mic_recording))
                    statusText?.text = getString(R.string.listening_text)
                    processingIndicator?.visibility = View.GONE
                    // Animación de pulso para el micrófono
                    micButton?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(300)?.start()
                }
                UIState.PROCESSING -> {
                    micContainer?.isEnabled = false
                    micButton?.alpha = 0.7f
                    statusIcon?.setColorFilter(ContextCompat.getColor(this, R.color.mic_processing))
                    statusText?.text = getString(R.string.processing_text)
                    processingIndicator?.visibility = View.VISIBLE
                    // Restaurar tamaño del micrófono
                    micButton?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(300)?.start()
                }
            }
        }
    }

    private fun showStatusBar() {
        statusBar?.visibility = View.VISIBLE
    }

    private fun hideStatusBar() {
        statusBar?.visibility = View.GONE
        micButton?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(200)?.start()
    }

    private fun performVibration() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.vibrate(50)
    }

    private fun showError(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        updateUIState(UIState.IDLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        audioRecorder.release()
        whisperEngine.release()
        llmEngine.release()
    }

    enum class UIState {
        IDLE,
        RECORDING,
        PROCESSING
    }

    enum class Language {
        AUTO,
        SPANISH,
        CATALAN
    }
}
