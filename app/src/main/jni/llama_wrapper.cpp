#include <jni.h>
#include <string>
#include <vector>
#include "llama.h"

// Contexto nativo de Llama
struct LlamaContext {
    struct llama_model* model;
    struct llama_context* ctx;
};

extern "C" {

/**
 * Inicializa el contexto de Llama con el modelo especificado
 */
JNIEXPORT jlong JNICALL
Java_com_voicetype_keyboard_LLMEngine_initContext(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* model_path = env->GetStringUTFChars(modelPath, nullptr);
    
    // Inicializar modelo GGUF
    struct llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Usar CPU
    
    struct llama_model* model = llama_model_load_from_file(model_path, model_params);
    
    env->ReleaseStringUTFChars(modelPath, model_path);
    
    if (!model) {
        return 0;
    }
    
    // Inicializar contexto
    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
    
    struct llama_context* ctx = llama_init_from_model(model, ctx_params);
    
    if (!ctx) {
        llama_model_free(model);
        return 0;
    }
    
    LlamaContext* wrapper = new LlamaContext{model, ctx};
    return reinterpret_cast<jlong>(wrapper);
}

/**
 * Libera el contexto de Llama
 */
JNIEXPORT void JNICALL
Java_com_voicetype_keyboard_LLMEngine_freeContext(JNIEnv* env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) return;
    
    LlamaContext* wrapper = reinterpret_cast<LlamaContext*>(contextPtr);
    
    llama_free(wrapper->ctx);
    llama_model_free(wrapper->model);
    delete wrapper;
}

/**
 * Genera texto a partir del prompt
 */
JNIEXPORT jstring JNICALL
Java_com_voicetype_keyboard_LLMEngine_generateText(
    JNIEnv* env,
    jobject thiz,
    jlong contextPtr,
    jstring prompt,
    jint maxTokens,
    jfloat temperature
) {
    if (contextPtr == 0) {
        return nullptr;
    }
    
    LlamaContext* wrapper = reinterpret_cast<LlamaContext*>(contextPtr);
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    // Tokenizar prompt
    std::vector<llama_token> tokens;
    tokens.resize(1024);
    
    int n_tokens = llama_tokenize(
        llama_model_get_model(wrapper->model),
        prompt_str,
        strlen(prompt_str),
        tokens.data(),
        tokens.size(),
        true,  // add_special
        true   // parse_special
    );
    
    if (n_tokens < 0) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return nullptr;
    }
    
    tokens.resize(n_tokens);
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    // Configurar parámetros de sampling
    struct llama_sampling_params sparams;
    sparams.temp = temperature;
    sparams.top_k = 40;
    sparams.top_p = 0.9f;
    sparams.penalty_last_n = 64;
    sparams.penalty_repeat = 1.1f;
    
    // Evaluar prompt
    if (llama_decode(wrapper->ctx, llama_batch_get_one(tokens.data(), tokens.size())) != 0) {
        return nullptr;
    }
    
    // Generar respuesta
    std::string result;
    int n_predict = maxTokens;
    
    for (int i = 0; i < n_predict; ++i) {
        // Samplear siguiente token
        llama_token new_token = llama_sample_token_greedy(wrapper->ctx, nullptr);
        
        if (new_token == llama_token_eos(llama_model_get_model(wrapper->model))) {
            break;
        }
        
        // Convertir token a string
        char buf[256];
        int n_chars = llama_token_to_piece(
            llama_model_get_model(wrapper->model),
            new_token,
            buf,
            sizeof(buf),
            0,
            true
        );
        
        if (n_chars > 0) {
            result.append(buf, n_chars);
        }
        
        // Evaluar nuevo token
        if (llama_decode(wrapper->ctx, llama_batch_get_one(&new_token, 1)) != 0) {
            break;
        }
    }
    
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
