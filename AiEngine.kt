package com.personalai.assistant

/**
 * Contract for the AI engines we will add in later stages.
 * The current v1 starter intentionally does not pretend to be an AI model.
 */
interface AiEngine {
    suspend fun reply(prompt: String, history: List<ChatMessage>): String
}

class OfflineAiEngine : AiEngine {
    override suspend fun reply(prompt: String, history: List<ChatMessage>): String {
        return "Offline AI engine is not connected yet. In the next development step we will add a compatible GGUF model and native inference runtime."
    }
}

class OnlineAiEngine : AiEngine {
    override suspend fun reply(prompt: String, history: List<ChatMessage>): String {
        return "Online AI service is not connected yet. We will connect a secure backend/API in a later step. Never put a private API key directly inside an APK."
    }
}
