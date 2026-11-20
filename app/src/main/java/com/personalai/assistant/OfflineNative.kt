package com.personalai.assistant

object OfflineNative {

    init {
        System.loadLibrary("personal_ai_native")
    }

    @Volatile
    private var loadedPath: String? = null

    external fun loadModel(modelPath: String): String

    external fun generate(prompt: String): String

    @Synchronized
    fun ensureLoaded(modelPath: String): String {
        if (loadedPath == modelPath) {
            return "MODEL_LOADED"
        }

        val status = loadModel(modelPath)

        if (status == "MODEL_LOADED") {
            loadedPath = modelPath
        }

        return status
    }
}
