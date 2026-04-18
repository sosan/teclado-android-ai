//package com.voicetype.keyboard
//
//import android.content.Context
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.io.File
//
///**
// * LLMEngine - Motor de procesamiento de texto usando Phi-3 Mini (llama.cpp)
// *
// * Características:
// * - Modelo: phi-3-mini-4k-instruct.Q4_K_M.gguf (4-bit cuantizado)
// * - Objetivo: Limpiar transcripción, eliminar muletillas, corregir gramática
// * - Idiomas: Detecta automáticamente Español/Catalán y mantiene el idioma
// *
// * Prompt usado:
// * "Clean and rewrite the following speech transcription.
// *  - Detect if it's Spanish or Catalan and keep the same language
// *  - Remove filler words (eh, mm, vale, bueno...)
// *  - Fix grammar and punctuation
// *  - Keep the original meaning
// *  - Make it natural and concise"
// */
//class LLMEngine {
//
//    companion object {
//        private const val MODEL_PATH = "models/phi-3-mini-4k-instruct.Q4_K_M.gguf"
//
//        private val SYSTEM_PROMPT = """
//            Eres un asistente que limpia y mejora transcripciones de voz.
//            - Detecta si el texto es en español o catalán y MANTIÉN el mismo idioma
//            - Elimina muletillas (eh, mm, vale, bueno, este, o sea, etc.)
//            - Corrige gramática y puntuación
//            - Mantén el significado original
//            - Hazlo natural y conciso
//            - Devuelve SOLO el texto limpio, sin explicaciones
//        """.trimIndent()
//
//        // Cargar librería nativa
//        init {
//            try {
//                System.loadLibrary("llama")
//            } catch (e: UnsatisfiedLinkError) {
//                // La librería se cargará cuando esté disponible
//            }
//        }
//    }
//
//    private var nativeContext: Long = 0
//    private var isInitialized = false
//
//    /**
//     * Inicializa el motor con el modelo desde assets
//     */
//    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
//        try {
//            val modelFile = copyModelFromAssets(context)
//            nativeContext = initContext(modelFile.absolutePath)
//            isInitialized = nativeContext != 0L
//            isInitialized
//        } catch (e: Exception) {
//            e.printStackTrace()
//            false
//        }
//    }
//
//    /**
//     * Mejora y limpia el texto transcrito
//     *
//     * @param inputText Texto transcrito por Whisper
//     * @return Texto limpio y natural
//     */
//    suspend fun improveText(inputText: String): String = withContext(Dispatchers.IO) {
//        if (!isInitialized) {
//            // Si no está inicializado, devolver texto original como fallback
//            return@withContext postProcessFallback(inputText)
//        }
//
//        try {
//            val prompt = buildPrompt(inputText)
//
//            val result = generateText(
//                context = nativeContext,
//                prompt = prompt,
//                maxTokens = 150,
//                temperature = 0.3f
//            )
//
//            // Extraer solo la respuesta útil
//            result?.let { cleanResponse(it) } ?: postProcessFallback(inputText)
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            postProcessFallback(inputText)
//        }
//    }
//
//    /**
//     * Construye el prompt para el LLM
//     */
//    private fun buildPrompt(inputText: String): String {
//        return """
//            <|system|>
//            $SYSTEM_PROMPT<|end|>
//            <|user|>
//            Transcripción: "$inputText"
//
//            Texto limpio:<|end|>
//            <|assistant|>
//        """.trimIndent()
//    }
//
//    /**
//     * Limpia la respuesta del LLM para obtener solo el texto útil
//     */
//    private fun cleanResponse(response: String): String {
//        return response
//            .trim()
//            .removePrefix("\"")
//            .removeSuffix("\"")
//            .trim()
//    }
//
//    /**
//     * Fallback: limpieza básica sin LLM (reglas simples)
//     */
//    private fun postProcessFallback(text: String): String {
//        return text
//            // Eliminar muletillas comunes en español
//            .replace(Regex("\\b(eh|mm|um|este|o sea|bueno|vale|pues|mira|verás)\\b", RegexOption.IGNORE_CASE), "")
//            // Eliminar muletillas comunes en catalán
//            .replace(Regex("\\b(eh|mm|um|doncs|bé|miri|vull dir|és a dir)\\b", RegexOption.IGNORE_CASE), "")
//            // Eliminar espacios múltiples
//            .replace(Regex("\\s+"), " ")
//            // Capitalizar primera letra
//            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
//            // Asegurar punto final si no hay puntuación
//            .let { if (it.lastOrNull() !in listOf('.', '?', '!')) "$it." else it }
//            .trim()
//    }
//
//    /**
//     * Copia el modelo desde assets al directorio de caché
//     */
//    private fun copyModelFromAssets(context: Context): File {
//        val cacheDir = context.cacheDir
//        val modelFile = File(cacheDir, MODEL_PATH)
//
//        if (!modelFile.exists()) {
//            modelFile.parentFile?.mkdirs()
//            context.assets.open(MODEL_PATH).use { input ->
//                modelFile.outputStream().use { output ->
//                    input.copyTo(output)
//                }
//            }
//        }
//
//        return modelFile
//    }
//
//    /**
//     * Libera recursos nativos
//     */
//    fun release() {
//        if (nativeContext != 0L) {
//            freeContext(nativeContext)
//            nativeContext = 0
//            isInitialized = false
//        }
//    }
//
//    // Métodos nativos implementados en llama_wrapper.cpp
//
//    private external fun initContext(modelPath: String): Long
//    private external fun freeContext(context: Long)
//
//    private external fun generateText(
//        context: Long,
//        prompt: String,
//        maxTokens: Int,
//        temperature: Float
//    ): String?
//}
package com.example.audioprocessing

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class LLMEngine(context: Context) {

    companion object {
        const val TAG = "LLMEngine"
        // Valores de configuración para el modelo Qwen2.5-1.5B-Instruct
        private const val N_CTX = 2048L
        private const val N_THREADS = 4L
        private const val N_BATCH = 512L
        private const val TEMP = 0.1f
        private const val TOP_K = 50
        private const val TOP_P = 0.9f
        private const val REPEAT_PENALTY = 1.1f
        private const val N_PREDICT = 512L
        private const val N_PROBS = 0L // Desactivado para mejor rendimiento
        private const val MIN_KEEP = 1L
        private const val SEED = -1L // Aleatorio

        init {
            System.loadLibrary("llm_engine_jni")
        }
    }

    // Declaraciones nativas
    private external fun nativeInit(
        modelPath: String,
        nCtx: Long,
        nThreads: Long,
        nBatch: Long,
        temp: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        nPredict: Long,
        nProbs: Long,
        minKeep: Long,
        seed: Long
    ): Long

    private external fun nativeGenerate(llmHandle: Long, prompt: String): String
    private external fun nativeFree(llmHandle: Long)

    private var llmHandle: Long = 0L
    private val modelFile: File

    init {
        // Copia el modelo desde los recursos crudos al directorio interno
        modelFile = File(context.getDir("models", Context.MODE_PRIVATE), "qwen2.5-1.5b-instruct-q4_k_m.gguf")
        if (!modelFile.exists()) {
            Log.d(TAG, "Modelo no encontrado, copiando desde recursos...")
            copyModelFromAssets(context, modelFile)
        } else {
            Log.d(TAG, "Modelo ya existe en: ${modelFile.absolutePath}")
        }
    }

    /**
     * Carga el modelo en memoria.
     */
    fun loadModel(): Boolean {
        if (llmHandle != 0L) {
            Log.w(TAG, "El modelo ya está cargado.")
            return true
        }

        Log.d(TAG, "Cargando modelo desde: ${modelFile.absolutePath} ...")

        try {
            llmHandle = nativeInit(
                modelFile.absolutePath,
                N_CTX,
                N_THREADS,
                N_BATCH,
                TEMP,
                TOP_K,
                TOP_P,
                REPEAT_PENALTY,
                N_PREDICT,
                N_PROBS,
                MIN_KEEP,
                SEED
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Error al cargar la librería JNI o inicializar el modelo: ${e.message}", e)
            return false
        }

        if (llmHandle == 0L) {
            Log.e(TAG, "La inicialización nativa del modelo falló. Handle es 0.")
            return false
        }

        Log.i(TAG, "Modelo cargado exitosamente.")
        return true
    }

    /**
     * Libera los recursos del modelo si está cargado.
     */
    fun freeModel() {
        if (llmHandle != 0L) {
            nativeFree(llmHandle)
            llmHandle = 0L
            Log.i(TAG, "Modelo liberado de la memoria.")
        } else {
            Log.w(TAG, "No hay modelo cargado para liberar.")
        }
    }

    /**
     * Genera una respuesta basada en un prompt.
     * Asegúrate de que el modelo esté cargado antes de llamar a esta función.
     */
    fun generate(prompt: String): String {
        if (llmHandle == 0L) {
            Log.e(TAG, "El modelo no está cargado. Llama a loadModel() primero.")
            return ""
        }
        return nativeGenerate(llmHandle, prompt).trim()
    }

    /**
     * Crea un prompt de chat formateado para el modelo Qwen.
     * El sistema pide al modelo que actúe como un corrector ortográfico y gramatical
     * para transcripciones de voz en Español y Catalán.
     */
    fun createChatPrompt(userMessage: String): String {
        val systemMessage = """
            Eres un corrector ortográfico y gramatical experto en Español y Catalán.
            Tu tarea es recibir una transcripción de voz que puede contener errores debidos a la conversión audio-texto.
            Debes corregir todos los errores gramaticales, ortográficos y de puntuación que encuentres.
            Mantén el significado original del texto lo más fielmente posible.
            Devuelve únicamente el texto corregido, sin añadir comentarios ni explicaciones adicionales.
        """.trimIndent()

        return "<|system|>\n$systemMessage\n<|endoftext|>\n<|user|>\n$userMessage\n<|endoftext|>\n<|assistant|>\n"
    }

    @Throws(IOException::class)
    private fun copyModelFromAssets(context: Context, outputFile: File) {
        val assetManager: InputStream? = context.assets.open("qwen2.5-1.5b-instruct-q4_k_m.gguf")
        val out = FileOutputStream(outputFile)
        assetManager?.copyTo(out)
        assetManager?.close()
        out.close()
    }

    /**
     * Verifica si el modelo está cargado en memoria.
     */
    fun isModelLoaded(): Boolean {
        return llmHandle != 0L
    }
}