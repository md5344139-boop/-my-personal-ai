#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "PersonalAI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static llama_sampler *g_sampler = nullptr;
static const llama_vocab *g_vocab = nullptr;
static std::mutex g_mutex;
static bool g_backend_initialized = false;

static void release_model() {
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_personalai_assistant_OfflineNative_loadModel(
        JNIEnv *env, jobject, jstring modelPath) {

    std::lock_guard<std::mutex> lock(g_mutex);

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return env->NewStringUTF("MODEL_PATH_ERROR");

    release_model();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        return env->NewStringUTF("MODEL_LOAD_FAILED");
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 512;
    cp.n_batch = 512;
    cp.n_ubatch = 128;
    cp.n_threads = 2;
    cp.n_threads_batch = 2;

    g_ctx = llama_init_from_model(g_model, cp);

    if (!g_ctx) {
        release_model();
        return env->NewStringUTF("CONTEXT_CREATE_FAILED");
    }

    g_vocab = llama_model_get_vocab(g_model);

    llama_sampler_chain_params sp =
        llama_sampler_chain_default_params();

    g_sampler = llama_sampler_chain_init(sp);

    if (!g_sampler) {
        release_model();
        return env->NewStringUTF("SAMPLER_CREATE_FAILED");
    }

    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.70f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return env->NewStringUTF("MODEL_LOADED");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_personalai_assistant_OfflineNative_generate(
        JNIEnv *env, jobject, jstring userPrompt) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model || !g_ctx || !g_vocab || !g_sampler) {
        return env->NewStringUTF("MODEL_NOT_LOADED");
    }

    const char *input = env->GetStringUTFChars(userPrompt, nullptr);
    if (!input) return env->NewStringUTF("INPUT_ERROR");

    std::string prompt =
        "<|im_start|>system\n"
        "You are a helpful AI assistant. Answer clearly in Bengali.\n"
        "<|im_end|>\n"
        "<|im_start|>user\n" +
        std::string(input) +
        "\n<|im_end|>\n"
        "<|im_start|>assistant\n";

    env->ReleaseStringUTFChars(userPrompt, input);

    llama_memory_clear(llama_get_memory(g_ctx), true);
    llama_sampler_reset(g_sampler);

    std::vector<llama_token> tokens(prompt.size() + 16);

    int32_t token_count = llama_tokenize(
        g_vocab,
        prompt.c_str(),
        (int32_t) prompt.size(),
        tokens.data(),
        (int32_t) tokens.size(),
        true,
        true
    );

    if (token_count < 0) {
        return env->NewStringUTF("TOKENIZE_FAILED");
    }

    tokens.resize(token_count);

    llama_batch_ext *batch = llama_batch_ext_init(g_ctx);
    if (!batch) return env->NewStringUTF("BATCH_CREATE_FAILED");

    for (size_t i = 0; i < tokens.size(); ++i) {
        int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);

        if (idx < 0) {
            llama_batch_ext_free(batch);
            return env->NewStringUTF("BATCH_ADD_FAILED");
        }

        llama_pos pos = (llama_pos) i;

        if (!llama_batch_ext_set_pos(batch, idx, &pos)) {
            llama_batch_ext_free(batch);
            return env->NewStringUTF("POSITION_FAILED");
        }
    }

    int32_t result = llama_process(
        g_ctx,
        LLAMA_PROCESS_TYPE_DECODE,
        batch
    );

    llama_batch_ext_free(batch);

    if (result != 0) {
        return env->NewStringUTF("PROMPT_DECODE_FAILED");
    }

    std::string answer;
    llama_pos current_pos = (llama_pos) tokens.size();

    for (int i = 0; i < 128; ++i) {
        llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (llama_vocab_is_eog(g_vocab, token)) {
            break;
        }

        char piece[256];

        int32_t piece_size = llama_token_to_piece(
            g_vocab, token, piece, sizeof(piece), 0, false
        );

        if (piece_size < 0) {
            break;
        }

        answer.append(piece, piece_size);

        llama_batch_ext *next_batch = llama_batch_ext_init(g_ctx);
        if (!next_batch) break;

        int32_t idx = llama_batch_ext_add_token(next_batch, 0, token);

        if (idx < 0) {
            llama_batch_ext_free(next_batch);
            break;
        }

        llama_pos pos = current_pos++;

        if (!llama_batch_ext_set_pos(next_batch, idx, &pos)) {
            llama_batch_ext_free(next_batch);
            break;
        }

        int32_t decode_result = llama_process(
            g_ctx,
            LLAMA_PROCESS_TYPE_DECODE,
            next_batch
        );

        llama_batch_ext_free(next_batch);

        if (decode_result != 0) break;
    }

    if (answer.empty()) {
        answer = "দুঃখিত, উত্তর তৈরি করা যায়নি।";
    }

    return env->NewStringUTF(answer.c_str());
}
