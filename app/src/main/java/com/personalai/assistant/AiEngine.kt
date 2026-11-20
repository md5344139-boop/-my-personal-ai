package com.personalai.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface AiEngine {
    fun reply(prompt: String, history: List<ChatMessage>): String
}

class OnlineAiEngine : AiEngine {

    override fun reply(
        prompt: String,
        history: List<ChatMessage>
    ): String {
        var connection: HttpURLConnection? = null

        return try {
            connection =
                URL(WORKER_URL).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 30000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            val historyJson = JSONArray()

            history.takeLast(20).forEach { item ->
                historyJson.put(JSONObject().apply {
                    put(
                        "role",
                        if (item.fromUser) "user" else "model"
                    )
                    put("text", item.text)
                })
            }

            val request = JSONObject().apply {
                put("message", prompt)
                put("history", historyJson)
            }

            connection.outputStream.use { output ->
                output.write(request.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()

            if (responseText.isBlank()) {
                "সার্ভার থেকে খালি উত্তর এসেছে। আবার চেষ্টা করো।"
            } else {
                val response = JSONObject(responseText)

                if (code !in 200..299) {
                    "সার্ভার সমস্যা ($code): ${
                        response.optString("error", responseText)
                    }"
                } else {
                    response.optString(
                        "reply",
                        "AI থেকে উত্তর পাওয়া যায়নি।"
                    )
                }
            }
        } catch (e: Exception) {
            "সংযোগ সমস্যা: ${e.message ?: "ইন্টারনেট সংযোগ পরীক্ষা করো।"}"
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val WORKER_URL =
            "https://gentle-glitter-dd66.md5344139.workers.dev/"
    }
}
