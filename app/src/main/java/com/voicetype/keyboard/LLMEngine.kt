package com.voicetype.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LLMEngine - Motor de procesamiento de texto usando Phi-3 Mini (llama.cpp)
 * 
 * Características:
 * - Modelo: phi-3-mini-4k-instruct.Q4_K_M.gguf (4-bit cuantizado)
 * - Objetivo: Limpiar transcripción, eliminar muletillas, corregir gramática
 * - Idiomas: Detecta automáticamente Español/Catalán y mantiene el idioma
 * 
 * Prompt usado:
 * "Clean and rewrite the following speech transcription.
 *  - Detect if it's Spanish or Catalan and keep the same language
 *  - Remove filler words (eh, mm, vale, bueno...)
 *  - Fix grammar and punctuation
 *  - Keep the original meaning
 *  - Make it natural and concise"
 */
class LLMEngine {

    companion object {
        private const val MODEL_PATH = "models/phi-3-mini-4k-instruct.Q4_K_M.gguf"
        
        private const val SYSTEM_PROMPT = """
            Eres un asistente que limpia y mejora transcripciones de voz.
            - Detecta si el texto es en español o catalán y MANTIÉN el mismo idioma
            - Elimina muletillas (eh, mm, vale, bueno, este, o sea, etc.)
            - Corrige gramática y puntuación
            - Mantén el significado original
            - Hazlo natural y conciso
            - Devuelve SOLO el texto limpio, sin explicaciones
        """.trimIndent()
        
        // Cargar librería nativa
        init {
            try {
                System.loadLibrary("llama")
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
            val modelFile = copyModelFromAssets(context)
            nativeContext = initContext(modelFile.absolutePath)
            isInitialized = nativeContext != 0L
            isInitialized
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Mejora y limpia el texto transcrito
     * 
     * @param inputText Texto transcrito por Whisper
     * @return Texto limpio y natural
     */
    suspend fun improveText(inputText: String): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            // Si no está inicializado, devolver texto original como fallback
            return@withContext postProcessFallback(inputText)
        }

        try {
            val prompt = buildPrompt(inputText)
            
            val result = generateText(
                context = nativeContext,
                prompt = prompt,
                maxTokens = 150,
                temperature = 0.3f
            )
            
            // Extraer solo la respuesta útil
            result?.let { cleanResponse(it) } ?: postProcessFallback(inputText)
            
        } catch (e: Exception) {
            e.printStackTrace()
            postProcessFallback(inputText)
        }
    }

    /**
     * Construye el prompt para el LLM
     */
    private fun buildPrompt(inputText: String): String {
        return """
            <|system|>
            $SYSTEM_PROMPT<|end|>
            <|user|>
            Transcripción: "$inputText"
            
            Texto limpio:<|end|>
            <|assistant|>
        """.trimIndent()
    }

    /**
     * Limpia la respuesta del LLM para obtener solo el texto útil
     */
    private fun cleanResponse(response: String): String {
        return response
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .trim()
    }

    /**
     * Fallback: limpieza básica sin LLM (reglas simples)
     */
    private fun postProcessFallback(text: String): String {
        return text
            // Eliminar muletillas comunes en español
            .replace(Regex("\\b(eh|mm|um|este|o sea|bueno|vale|pues|mira|verás)\\b", RegexOption.IGNORE_CASE), "")
            // Eliminar muletillas comunes en catalán
            .replace(Regex("\\b(eh|mm|um|doncs|bé|miri|vull dir|és a dir)\\b", RegexOption.IGNORE_CASE), "")
            // Eliminar espacios múltiples
            .replace(Regex("\\s+"), " ")
            // Capitalizar primera letra
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            // Asegurar punto final si no hay puntuación
            .let { if (it.lastOrNull() !in listOf('.', '?', '!')) "$it." else it }
            .trim()
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

    // Métodos nativos implementados en llama_wrapper.cpp
    
    private external fun initContext(modelPath: String): Long
    private external fun freeContext(context: Long)
    
    private external fun generateText(
        context: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): String?
}
