package com.voicetype.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.voicetype.keyboard.AudioRecorder.OnRecordingCompleteListener
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VoiceTypeIME : InputMethodService() {

    // --- Vistas del Layout XML ---
    private lateinit var rootView: View
    private lateinit var btnMic: ImageButton
    private lateinit var btnSpace: ImageButton
    private lateinit var btnBackspace: ImageButton
    private lateinit var btnEnter: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnLanguage: ImageButton
    
    private lateinit var statusContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivWaveform: ImageView // Opcional: animación visual

    // --- Motores ---
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisperEngine: WhisperEngine
    private lateinit var llmEngine: LLMEngine

    // --- Hilos y Estado ---
    private val executor = Executors.newSingleThreadExecutor()
    private var isRecording = false
    private var isProcessing = false

    // --- Configuración ---
    private var currentLanguage = "auto" // auto, es, ca

    override fun onCreate() {
        super.onCreate()
        initializeEngines()
    }

    private fun initializeEngines() {
        // 1. Grabadora de Audio
        audioRecorder = AudioRecorder(this)

        // 2. Motor Whisper (ASR) - Carga pesada, hacer en background si es posible
        // Para simplificar, cargamos al inicio. En prod, usar splash screen o carga diferida.
        executor.execute {
            whisperEngine = WhisperEngine(this)
            whisperEngine.loadModel() 
        }

        // 3. Motor LLM (Qwen2.5) - Carga muy pesada
        executor.execute {
            llmEngine = LLMEngine(this)
            llmEngine.loadModel()
        }
    }

    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.voice_keyboard_view, null)
        initViews()
        setupListeners()
        return rootView
    }

    private fun initViews() {
        btnMic = rootView.findViewById(R.id.btnMic)
        btnSpace = rootView.findViewById(R.id.btnSpace)
        btnBackspace = rootView.findViewById(R.id.btnBackspace)
        btnEnter = rootView.findViewById(R.id.btnEnter)
        btnSettings = rootView.findViewById(R.id.btnSettings)
        btnLanguage = rootView.findViewById(R.id.btnLanguage)

        statusContainer = rootView.findViewById(R.id.statusContainer)
        tvStatus = rootView.findViewById(R.id.tvStatus)
        progressBar = rootView.findViewById(R.id.progressBar)
        ivWaveform = rootView.findViewById(R.id.ivWaveform)
        
        // Estado inicial
        updateUiState(State.IDLE)
    }

    private fun setupListeners() {
        // 🎤 Botón Micrófono (Push-to-Talk)
        btnMic.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (!isProcessing) startRecording()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) stopRecording()
                    true
                }
                else -> false
            }
        }

        // ⌨️ Teclas estándar
        btnSpace.setOnClickListener { commitText(" ") }
        
        btnBackspace.setOnClickListener {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
        }
        btnBackspace.setOnLongClickListener {
            // Borrar palabra completa o línea (comportamiento estándar)
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
            true
        }

        btnEnter.setOnClickListener {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
        }

        btnSettings.setOnClickListener {
            launchSettingsActivity()
        }

        btnLanguage.setOnClickListener {
            cycleLanguage()
        }
    }

    // --- Lógica de Grabación ---

    private fun startRecording() {
        if (whisperEngine.isModelLoaded().not()) {
            Toast.makeText(this, "Cargando modelo de voz...", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        updateUiState(State.RECORDING)
        audioRecorder.startRecording(object : OnRecordingCompleteListener {
            override fun onRecordingComplete(audioPath: String) {
                // El audio está listo, pasar a procesamiento
                processAudioPipeline(audioPath)
            }

            override fun onError(error: String) {
                isRecording = false
                updateUiState(State.IDLE)
                Toast.makeText(this@VoiceTypeIME, "Error grabación: $error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun stopRecording() {
        isRecording = false
        audioRecorder.stopRecording()
        // La UI se actualizará cuando empiece el procesamiento
    }

    // --- Pipeline Principal: Whisper -> LLM -> Insertar ---

    private fun processAudioPipeline(audioPath: String) {
        if (isProcessing) return
        isProcessing = true
        updateUiState(State.PROCESSING_ASR)

        executor.execute {
            try {
                // 1. ASR (Whisper)
                val rawTranscription = whisperEngine.transcribe(audioPath, currentLanguage)
                
                if (rawTranscription.isBlank()) {
                    runOnUiThread { 
                        isProcessing = false
                        updateUiState(State.IDLE)
                        Toast.makeText(this@VoiceTypeIME, "No se detectó voz", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                // Feedback intermedio (opcional: mostrar texto crudo brevemente)
                runOnUiThread {
                    tvStatus.text = "Mejorando texto..."
                    updateUiState(State.PROCESSING_LLM)
                }

                // 2. LLM (Qwen2.5) - Limpieza y corrección
                // Si el LLM no ha cargado aún, usamos fallback
                val finalText = if (llmEngine.isModelLoaded()) {
                    val prompt = llmEngine.createChatPrompt(rawTranscription)
                    val llmResult = llmEngine.generate(prompt)
                    
                    // Post-procesado ligero por si el LLM devuelve algo raro
                    postProcessFallback(llmResult) 
                } else {
                    // Fallback si el LLM no está listo
                    postProcessFallback(rawTranscription)
                }

                // 3. Insertar texto
                runOnUiThread {
                    commitText(finalText)
                    isProcessing = false
                    updateUiState(State.IDLE)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    isProcessing = false
                    updateUiState(State.IDLE)
                    Toast.makeText(this@VoiceTypeIME, "Error procesando: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Post-Procesado de Seguridad (Fallback) ---
    
    private fun postProcessFallback(text: String): String {
        if (text.isBlank()) return text
        
        return text
            // Eliminar muletillas comunes (Español & Catalán)
            .replace(Regex("\\b(eh|mm|um|este|o sea|bueno|vale|pues|mira|verás|doncs|bé|miri|vull dir|és a dir)\\b", RegexOption.IGNORE_CASE), "")
            // Eliminar espacios múltiples
            .replace(Regex("\\s+"), " ")
            // Capitalizar primera letra
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            // Asegurar punto final si no hay puntuación
            .let { 
                val trimmed = it.trim()
                if (trimmed.isNotEmpty() && trimmed.lastOrNull() !in listOf('.', '?', '!', ',', ';', ':')) "$trimmed." else trimmed 
            }
            .trim()
    }

    // --- Gestión de UI ---

    private enum class State {
        IDLE,
        RECORDING,
        PROCESSING_ASR,
        PROCESSING_LLM
    }

    private fun updateUiState(state: State) {
        when (state) {
            State.IDLE -> {
                btnMic.setImageResource(R.drawable.ic_mic)
                btnMic.isEnabled = true
                statusContainer.visibility = View.GONE
                progressBar.isIndeterminate = false
            }
            State.RECORDING -> {
                btnMic.setImageResource(R.drawable.ic_mic_recording) // Icono rojo
                statusContainer.visibility = View.VISIBLE
                tvStatus.text = "Escuchando..."
                tvStatus.setTextColor(getColor(R.color.recording_red))
                progressBar.visibility = View.GONE
                // Aquí podrías iniciar animación de waveform
            }
            State.PROCESSING_ASR, State.PROCESSING_LLM -> {
                btnMic.isEnabled = false
                statusContainer.visibility = View.VISIBLE
                
                if (state == State.PROCESSING_ASR) {
                    tvStatus.text = "Transcribiendo..."
                    tvStatus.setTextColor(getColor(R.color.processing_orange))
                } else {
                    tvStatus.text = "Corrigiendo con IA..."
                    tvStatus.setTextColor(getColor(R.color.processing_blue))
                }
                
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
            }
        }
    }

    // --- Utilidades ---

    private fun commitText(text: String) {
        val ic = currentInputConnection
        ic?.commitText(text, 1)
    }

    private fun launchSettingsActivity() {
        val intent = android.content.Intent(this, com.voicetype.keyboard.ui.SettingsActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun cycleLanguage() {
        currentLanguage = when (currentLanguage) {
            "auto" -> "es"
            "es" -> "ca"
            "ca" -> "auto"
            else -> "auto"
        }
        
        val flag = when (currentLanguage) {
            "es" -> "🇪🇸"
            "ca" -> "🇨🇦"
            "auto" -> "🌐"
            else -> "🌐"
        }
        
        btnLanguage.text = flag
        Toast.makeText(this, "Idioma: ${getLanguageName(currentLanguage)}", Toast.LENGTH_SHORT).show()
    }

    private fun getLanguageName(code: String): String = when (code) {
        "es" -> "Español"
        "ca" -> "Català"
        "auto" -> "Auto-detect"
        else -> "Desconocido"
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
        
        // Liberar recursos nativos
        if (::whisperEngine.isInitialized) whisperEngine.freeModel()
        if (::llmEngine.isInitialized) llmEngine.freeModel()
        if (::audioRecorder.isInitialized) audioRecorder.release()
    }
}