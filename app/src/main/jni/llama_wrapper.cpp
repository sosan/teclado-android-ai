#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include "llama.h"

struct LlamaContext {
    struct llama_model* model;
    struct llama_context* ctx;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicetype_keyboard_LLMEngine_initContext(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* model_path = env->GetStringUTFChars(modelPath, nullptr);

    struct llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    struct llama_model* model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(modelPath, model_path);

    if (!model) return 0;

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    struct llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        llama_model_free(model);
        return 0;
    }

    return reinterpret_cast<jlong>(new LlamaContext{model, ctx});
}

JNIEXPORT void JNICALL
Java_com_voicetype_keyboard_LLMEngine_freeContext(JNIEnv* env, jobject thiz, jlong contextPtr) {
if (contextPtr == 0) return;
LlamaContext* wrapper = reinterpret_cast<LlamaContext*>(contextPtr);
llama_free(wrapper->ctx);
llama_model_free(wrapper->model);
delete wrapper;
}

JNIEXPORT jstring JNICALL
        Java_com_voicetype_keyboard_LLMEngine_generateText(
        JNIEnv* env, jobject thiz, jlong contextPtr, jstring prompt, jint maxTokens, jfloat temperature
) {
if (contextPtr == 0) return nullptr;

LlamaContext* wrapper = reinterpret_cast<LlamaContext*>(contextPtr);
const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

// FIX 1: Get Vocab for llama_tokenize
const struct llama_vocab* vocab = llama_model_get_vocab(wrapper->model);

std::vector<llama_token> tokens;
tokens.resize(strlen(prompt_str) + 1);

int n_tokens = llama_tokenize(
        vocab, // Use vocab instead of model
        prompt_str,
        strlen(prompt_str),
        tokens.data(),
        tokens.size(),
        true,
        true
);

env->ReleaseStringUTFChars(prompt, prompt_str);
if (n_tokens < 0) return nullptr;
tokens.resize(n_tokens);

// FIX 2: Initialize Sampler (modern way)
struct llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
if (temperature <= 0) {
llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
} else {
llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234)); // Seed
}

// Decode prompt
llama_decode(wrapper->ctx, llama_batch_get_one(tokens.data(), tokens.size()));

std::string result;
llama_token curr_token;
int n_curr = 0;

while (n_curr < maxTokens) {
// Sample next token
curr_token = llama_sampler_sample(sampler, wrapper->ctx, -1);

if (curr_token == llama_vocab_eos(vocab)) break;

// Convert to piece
char buf[128];
int n_chars = llama_token_to_piece(vocab, curr_token, buf, sizeof(buf), 0, true);
if (n_chars > 0) result.append(buf, n_chars);

// Decode next
if (llama_decode(wrapper->ctx, llama_batch_get_one(&curr_token, 1)) != 0) break;
n_curr++;
}

llama_sampler_free(sampler);
return env->NewStringUTF(result.c_str());
}

} // extern "C"