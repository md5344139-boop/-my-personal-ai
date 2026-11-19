package com.personalai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small local conversation store for the starter app.
 * For a larger production release, migrate this to Room.
 */
class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("personal_ai_chat", Context.MODE_PRIVATE)

    fun load(): List<ChatMessage> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ChatMessage(
                            id = item.optLong("id"),
                            text = item.optString("text"),
                            fromUser = item.optBoolean("fromUser"),
                            timestamp = item.optLong("timestamp")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("id", message.id)
                    put("text", message.text)
                    put("fromUser", message.fromUser)
                    put("timestamp", message.timestamp)
                }
            )
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove("messages").apply()
    }
}
