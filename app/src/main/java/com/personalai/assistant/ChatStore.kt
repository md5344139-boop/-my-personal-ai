package com.personalai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Conversation(
    val id: String,
    var title: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var updatedAt: Long = System.currentTimeMillis()
)

class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "personal_ai_chat", Context.MODE_PRIVATE
    )

    fun loadConversations(): MutableList<Conversation> {
        val raw = prefs.getString("conversations_v2", null)
        if (raw != null) {
            return try {
                val array = JSONArray(raw)
                val result = mutableListOf<Conversation>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val messages = mutableListOf<ChatMessage>()
                    val items = obj.optJSONArray("messages") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        val m = items.getJSONObject(j)
                        messages.add(
                            ChatMessage(
                                m.optLong("id"),
                                m.optString("text"),
                                m.optBoolean("fromUser"),
                                m.optLong("timestamp")
                            )
                        )
                    }
                    result.add(
                        Conversation(
                            obj.optString("id"),
                            obj.optString("title", "নতুন চ্যাট"),
                            messages,
                            obj.optLong("updatedAt")
                        )
                    )
                }
                result
            } catch (_: Exception) {
                mutableListOf()
            }
        }

        // পুরোনো একক চ্যাটকে নতুন চ্যাট ব্যবস্থায় স্থানান্তর
        val old = load()
        val migrated = mutableListOf<Conversation>()
        if (old.isNotEmpty()) {
            val first = old.firstOrNull { it.fromUser }?.text
                ?.take(32) ?: "পুরোনো চ্যাট"
            migrated.add(
                Conversation("legacy", first, old.toMutableList())
            )
        }
        saveConversations(migrated)
        return migrated
    }

    fun saveConversations(items: List<Conversation>) {
        val array = JSONArray()
        items.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { m ->
                messages.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("text", m.text)
                        put("fromUser", m.fromUser)
                        put("timestamp", m.timestamp)
                    }
                )
            }
            array.put(
                JSONObject().apply {
                    put("id", conversation.id)
                    put("title", conversation.title)
                    put("updatedAt", conversation.updatedAt)
                    put("messages", messages)
                }
            )
        }
        prefs.edit().putString("conversations_v2", array.toString()).apply()
    }

    // পুরোনো সংরক্ষিত মেসেজ পড়ার জন্য
    fun load(): List<ChatMessage> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ChatMessage(
                            item.optLong("id"),
                            item.optString("text"),
                            item.optBoolean("fromUser"),
                            item.optLong("timestamp")
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
        messages.forEach { m ->
            array.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("text", m.text)
                    put("fromUser", m.fromUser)
                    put("timestamp", m.timestamp)
                }
            )
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove("messages").apply()
    }
}
