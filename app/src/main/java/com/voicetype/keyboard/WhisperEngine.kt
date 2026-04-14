package com.voicetype.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WhisperEngine - Motor de transcripción ASR usando Whisper.cpp
 * 
 * Características:
 * - Modelo: ggml-base.bin
 * - Idiomas: Español 🇪🇸, Catalán 🇨🇦 (auto-detect)
 * - Formato audio: WAV mono 16kHz
 * 
 * Opciones de idioma:
 * - "auto" → autodetección (recomendado para mezcla ES/CA)
 * - "es" → forzar español
 * - "ca" → forzar catalán
 */
class WhisperEngine {

    companion object {
        private const val MODEL_PATH = "models/ggml-base.bin"
        
        // Cargar librería nativa
        init {
            try {
                System.loadLibrary("whisper")
            } catch (e: UnsatisfiedLinkError) {
                // La librería se cargará cuando esté disponible
            }
        }
    }

    private var nativeContext: Long = 0
    private var isInitialized = false

    /**
     * Inicializa el motor con el modelo desde assets
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Copiar modelo desde assets a cache
            val modelFile = copyModelFromAssets(context)
            
            // Inicializar contexto nativo
            nativeContext = initContext(modelFile.absolutePath)
            isInitialized = nativeContext != 0L
            
            isInitialized
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Transcribe un archivo de audio WAV
     * 
     * @param audioFile Archivo WAV mono 16kHz
     * @param language "auto", "es", o "ca"
     * @return Texto transcrito
     */
    suspend fun transcribe(audioFile: File, language: String = "auto"): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("WhisperEngine no inicializado. Llama a initialize() primero.")
        }

        if (!audioFile.exists()) {
            throw IllegalArgumentException("El archivo de audio no existe: ${audioFile.absolutePath}")
        }

        try {
            val result = transcribeAudio(
                context = nativeContext,
                audioPath = audioFile.absolutePath,
                language = language,
                translate = false
            )
            
            result ?: throw RuntimeException("Error en transcripción Whisper")
            
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Copia el modelo desde assets al directorio de caché
     */
    private fun copyModelFromAssets(context: Context): File {
        val cacheDir = context.cacheDir
        val modelFile = File(cacheDir, MODEL_PATH)
        
        if (!modelFile.exists()) {
            modelFile.parentFile?.mkdirs()
            context.assets.open(MODEL_PATH).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        return modelFile
    }

    /**
     * Libera recursos nativos
     */
    fun release() {
        if (nativeContext != 0L) {
            freeContext(nativeContext)
            nativeContext = 0
            isInitialized = false
        }
    }

    // Métodos nativos implementados en whisper_wrapper.cpp
    
    /**
     * Inicializa el contexto de Whisper con el modelo especificado
     */
    private external fun initContext(modelPath: String): Long

    /**
     * Libera el contexto de Whisper
     */
    private external fun freeContext(context: Long)

    /**
     * Transcribe audio y devuelve el texto
     * 
     * @param context Contexto nativo de Whisper
     * @param audioPath Ruta al archivo WAV
     * @param language Código de idioma ("auto", "es", "ca")
     * @param translate Si true, traduce a inglés (false = transcribe en idioma original)
     * @return Texto transcrito o null si hay error
     */
    private external fun transcribeAudio(
        context: Long,
        audioPath: String,
        language: String,
        translate: Boolean
    ): String?
}
