package com.personalai.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface AiEngine {
    fun reply(prompt: String, history: List<ChatMessage>): String
}

class OfflineAiEngine : AiEngine {
    override fun reply(prompt: String, history: List<ChatMessage>): String {
        return "Offline AI model এখনো সংযুক্ত করা হয়নি।"
    }
}

class OnlineAiEngine : AiEngine {
    override fun reply(prompt: String, history: List<ChatMessage>): String {
        val connection = URL(WORKER_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val historyJson = JSONArray()
            history.takeLast(20).forEach {
                historyJson.put(JSONObject().apply {
                    put("role", if (it.fromUser) "user" else "model")
                    put("text", it.text)
                })
            }

            val request = JSONObject().apply {
                put("message", prompt)
                put("history", historyJson)
            }

            connection.outputStream.use {
                it.write(request.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream
                         else connection.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }
            val response = JSONObject(responseText)

            if (code !in 200..299) {
                return "সার্ভার সমস্যা ($code): ${response.optString("error", responseText)}"
            }

            return response.optString("reply", "AI থেকে উত্তর পাওয়া যায়নি।")
        } catch (e: Exception) {
            return "সংযোগ সমস্যা: ${e.message ?: "আবার চেষ্টা করুন।"}"
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val WORKER_URL =
            "https://gentle-glitter-dd66.md5344139.workers.dev/"
    }
}
