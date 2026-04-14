#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

// Contexto nativo de Whisper
struct WhisperContext {
    struct whisper_context* ctx;
};

extern "C" {

/**
 * Inicializa el contexto de Whisper con el modelo especificado
 */
JNIEXPORT jlong JNICALL
Java_com_voicetype_keyboard_WhisperEngine_initContext(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* model_path = env->GetStringUTFChars(modelPath, nullptr);
    
    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false; // Usar CPU para compatibilidad universal
    
    struct whisper_context* ctx = whisper_init_from_file_with_params(
        model_path, 
        params
    );
    
    env->ReleaseStringUTFChars(modelPath, model_path);
    
    if (!ctx) {
        return 0;
    }
    
    WhisperContext* wrapper = new WhisperContext{ctx};
    return reinterpret_cast<jlong>(wrapper);
}

/**
 * Libera el contexto de Whisper
 */
JNIEXPORT void JNICALL
Java_com_voicetype_keyboard_WhisperEngine_freeContext(JNIEnv* env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) return;
    
    WhisperContext* wrapper = reinterpret_cast<WhisperContext*>(contextPtr);
    whisper_free(wrapper->ctx);
    delete wrapper;
}

/**
 * Lee archivo WAV y convierte a samples flotantes
 */
static std::vector<float> loadWavFile(const std::string& filename) {
    // Implementación simplificada - en producción usar libsndfile o similar
    std::vector<float> samples;
    
    FILE* file = fopen(filename.c_str(), "rb");
    if (!file) return samples;
    
    // Leer header WAV (44 bytes)
    unsigned char header[44];
    fread(header, 1, 44, file);
    
    // Leer datos PCM 16-bit
    std::vector<short> pcm_data;
    short sample;
    while (fread(&sample, sizeof(short), 1, file) == 1) {
        pcm_data.push_back(sample);
    }
    
    fclose(file);
    
    // Convertir a float [-1.0, 1.0]
    samples.reserve(pcm_data.size());
    for (short s : pcm_data) {
        samples.push_back(static_cast<float>(s) / 32768.0f);
    }
    
    return samples;
}

/**
 * Transcribe audio y devuelve el texto
 */
JNIEXPORT jstring JNICALL
Java_com_voicetype_keyboard_WhisperEngine_transcribeAudio(
    JNIEnv* env, 
    jobject thiz,
    jlong contextPtr,
    jstring audioPath,
    jstring language,
    jboolean translate
) {
    if (contextPtr == 0) {
        return nullptr;
    }
    
    WhisperContext* wrapper = reinterpret_cast<WhisperContext*>(contextPtr);
    const char* audio_path = env->GetStringUTFChars(audioPath, nullptr);
    const char* lang = env->GetStringUTFChars(language, nullptr);
    
    // Cargar audio
    std::vector<float> samples = loadWavFile(audio_path);
    env->ReleaseStringUTFChars(audioPath, audio_path);
    
    if (samples.empty()) {
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    
    // Configurar parámetros de transcripción
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.translate = translate;
    wparams.single_segment = true;
    
    // Configurar idioma
    if (std::string(lang) != "auto") {
        wparams.language = lang;
    }
    
    env->ReleaseStringUTFChars(language, lang);
    
    // Ejecutar transcripción
    int result = whisper_full(wrapper->ctx, wparams, samples.data(), samples.size());
    
    if (result != 0) {
        return nullptr;
    }
    
    // Obtener resultado
    int n_segments = whisper_full_n_segments(wrapper->ctx);
    std::string full_text;
    
    for (int i = 0; i < n_segments; ++i) {
        const char* text = whisper_full_get_segment_text(wrapper->ctx, i);
        if (text) {
            if (!full_text.empty()) full_text += " ";
            full_text += text;
        }
    }
    
    return env->NewStringUTF(full_text.c_str());
}

} // extern "C"
