package com.personalai.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: ChatStore
    private lateinit var messagesBox: LinearLayout
    private lateinit var input: EditText
    private lateinit var avatarView: ImageView

    private val conversations = mutableListOf<Conversation>()
    private var activeId = ""
    private val history: MutableList<ChatMessage>
        get() = conversations.firstOrNull { it.id == activeId }?.messages
            ?: mutableListOf()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var callMode = false
    private var lastAiText = ""
    private var speechLanguage = "bn-BD"
    private var selectedVoiceName = ""
    private var speechRate = 1.0f
    private var speechPitch = 1.0f

    private val bg = Color.rgb(13, 15, 27)
    private val panel = Color.rgb(27, 30, 49)
    private val muted = Color.rgb(157, 164, 190)
    private val accent = Color.rgb(157, 105, 235)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)
        conversations.addAll(store.loadConversations())

        if (conversations.isEmpty()) {
            conversations.add(Conversation("chat_${System.currentTimeMillis()}", "নতুন চ্যাট"))
        }
        activeId = conversations.first().id
        loadVoiceSettings()

        window.statusBarColor = bg
        window.navigationBarColor = bg

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyVoiceSettings()
            }
        }

        buildScreen()
        loadSavedAvatar()
        renderHistory()
    }

    private fun loadVoiceSettings() {
        val p = getSharedPreferences("voice_settings", MODE_PRIVATE)
        speechLanguage = p.getString("language", "bn-BD") ?: "bn-BD"
        selectedVoiceName = p.getString("voice", "") ?: ""
        speechRate = p.getFloat("rate", 1f)
        speechPitch = p.getFloat("pitch", 1f)
    }

    private fun saveVoiceSettings() {
        getSharedPreferences("voice_settings", MODE_PRIVATE).edit()
            .putString("language", speechLanguage)
            .putString("voice", selectedVoiceName)
            .putFloat("rate", speechRate)
            .putFloat("pitch", speechPitch)
            .apply()
    }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(bg)
        }
        setContentView(root)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(10))
        }

        val menu = label("☰", 24f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(12), dp(8))
            setOnClickListener { openChatManager() }
        }
        header.addView(menu)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        heading.addView(label("My Personal AI", 20f, Color.WHITE, true))
        heading.addView(label("ONLINE AI COMPANION", 10f, muted, true))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))

        val voiceButton = label("🎚", 20f, Color.WHITE).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { openVoiceStudio() }
        }
        header.addView(voiceButton)

        val callButton = label("☎", 19f, Color.WHITE, true).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(accent, 22)
            setOnClickListener { openCallScreen() }
        }
        header.addView(callButton)
        root.addView(header)

        val avatarPanel = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(panel, 18)
        }

        avatarView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(75, 57, 115), 50)
            clipToOutline = true
            setImageResource(android.R.drawable.ic_menu_myplaces)
            setColorFilter(Color.WHITE)
        }
        avatarPanel.addView(avatarView, LinearLayout.LayoutParams(dp(68), dp(68)))

        val avatarInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        avatarInfo.addView(label("Your AI Companion", 15f, Color.WHITE, true))
        avatarInfo.addView(label("Online • Ready to talk", 11f, Color.rgb(105, 224, 181)))
        avatarInfo.addView(label("ছবি বদলাও", 11f, Color.WHITE, true).apply {
            setPadding(0, dp(7), 0, 0)
            setOnClickListener { chooseAvatar() }
        })
        avatarPanel.addView(avatarInfo, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(avatarPanel, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })

        val chatTitle = label("", 12f, muted, true).apply {
            setPadding(dp(3), dp(5), 0, dp(7))
        }
        chatTitle.tag = "chat_title"
        root.addView(chatTitle)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        messagesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        scroll.addView(messagesBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(5), dp(6), dp(5))
            background = rounded(panel, 18)
        }

        composer.addView(label("🎙", 19f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { requestVoiceInput() }
        }, LinearLayout.LayoutParams(dp(42), dp(46)))

        input = EditText(this).apply {
            hint = "এখানে মেসেজ লিখো..."
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 4
            background = null
            setPadding(dp(5), dp(8), dp(4), dp(8))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        composer.addView(label("➤", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 30)
            setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(composer)

        root.addView(label("🔊  শেষ উত্তরটি ভয়েসে শোনো", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(8))
            setOnClickListener {
                if (lastAiText.isNotBlank()) speak(lastAiText)
                else Toast.makeText(this@MainActivity, "এখনো কোনো AI উত্তর নেই", Toast.LENGTH_SHORT).show()
            }
        })
        updateChatTitle()
    }

    private fun updateChatTitle() {
        val title = findViewById<View>(android.R.id.content)
            ?.findViewWithTag<TextView>("chat_title")
        title?.text = "CONVERSATION  •  ${conversations.firstOrNull { it.id == activeId }?.title ?: ""}"
    }

    private fun persist() = store.saveConversations(conversations)

    private fun openChatManager() {
        val options = arrayOf("＋ নতুন চ্যাট", "▤ চ্যাটের তালিকা", "✎ বর্তমান চ্যাটের নাম বদলাও", "🗑 বর্তমান চ্যাট ডিলিট")
        AlertDialog.Builder(this)
            .setTitle("চ্যাট মেনু")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> newChat()
                    1 -> showChatList()
                    2 -> renameChat()
                    3 -> deleteChat()
                }
            }.show()
    }

    private fun newChat() {
        val chat = Conversation("chat_${System.currentTimeMillis()}", "নতুন চ্যাট")
        conversations.add(0, chat)
        activeId = chat.id
        persist()
        renderHistory()
        updateChatTitle()
    }

    private fun showChatList() {
        if (conversations.isEmpty()) return
        val names = conversations.map { c ->
            (if (c.id == activeId) "✓ " else "") +
                c.title + "  (${c.messages.size})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("তোমার চ্যাটগুলো")
            .setItems(names) { _, which ->
                activeId = conversations[which].id
                lastAiText = conversations[which].messages.lastOrNull { !it.fromUser }?.text.orEmpty()
                renderHistory()
                updateChatTitle()
            }
            .setPositiveButton("＋ নতুন চ্যাট") { _, _ -> newChat() }
            .show()
    }

    private fun renameChat() {
        val current = conversations.firstOrNull { it.id == activeId } ?: return
        val field = EditText(this).apply {
            setText(current.title)
            setSelection(text.length)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("চ্যাটের নতুন নাম")
            .setView(field)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("সংরক্ষণ") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotEmpty()) {
                    current.title = name
                    persist()
                    updateChatTitle()
                }
            }.show()
    }

    private fun deleteChat() {
        if (conversations.size <= 1) {
            AlertDialog.Builder(this)
                .setMessage("শেষ চ্যাটটি ডিলিট করলে একটি নতুন খালি চ্যাট তৈরি হবে।")
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("ডিলিট") { _, _ ->
                    conversations.clear()
                    newChat()
                }.show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage("এই চ্যাটটি স্থায়ীভাবে ডিলিট করবে?")
            .setNegativeButton("না", null)
            .setPositiveButton("হ্যাঁ, ডিলিট") { _, _ ->
                conversations.removeAll { it.id == activeId }
                activeId = conversations.first().id
                persist()
                renderHistory()
                updateChatTitle()
            }.show()
    }

    private fun chooseAvatar() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, 2001)
    }

    private fun loadSavedAvatar() {
        val saved = getSharedPreferences("avatar", Context.MODE_PRIVATE)
            .getString("uri", null) ?: return
        try {
            avatarView.setImageURI(android.net.Uri.parse(saved))
            avatarView.clearColorFilter()
        } catch (_: Exception) {}
    }

    private fun openCallScreen() {
        callMode = true
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(20), dp(22), dp(20))
            setBackgroundColor(Color.rgb(17, 19, 34))
        }
        val callAvatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(75, 57, 115), 100)
            clipToOutline = true
            setImageDrawable(avatarView.drawable)
        }
        content.addView(callAvatar, LinearLayout.LayoutParams(dp(210), dp(250)).apply {
            bottomMargin = dp(18)
        })
        content.addView(label("তোমার AI Companion", 18f, Color.WHITE, true))
        content.addView(label("অনলাইন ভয়েস কথোপকথন", 12f, Color.rgb(105, 224, 181)).apply {
            setPadding(0, dp(7), 0, dp(20))
        })

        val dialog = AlertDialog.Builder(this).setView(content).create()
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(label("🎙 কথা বলো", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = rounded(accent, 24)
            setOnClickListener { requestVoiceInput() }
        })
        controls.addView(label("কল শেষ", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = rounded(Color.rgb(170, 48, 68), 24)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        content.addView(controls)
        dialog.setOnDismissListener { callMode = false }
        dialog.show()
    }

    private fun openVoiceStudio() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        layout.addView(label("কথা বলার ভাষা", 14f, Color.WHITE, true))
        val languages = listOf("বাংলা" to "bn-BD", "English (US)" to "en-US", "English (UK)" to "en-GB")
        val languageSpinner = Spinner(this)
        languageSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            languages.map { it.first }
        )
        languageSpinner.setSelection(languages.indexOfFirst { it.second == speechLanguage }.coerceAtLeast(0))
        layout.addView(languageSpinner)

        layout.addView(label("TTS Voice (ফোনে থাকা ভয়েস)", 14f, Color.WHITE, true).apply {
            setPadding(0, dp(14), 0, dp(4))
        })
        val voices = textToSpeech?.voices?.toList()
            ?.filter { !it.isNetworkConnectionRequired }
            ?.sortedBy { it.name } ?: emptyList()
        val voiceSpinner = Spinner(this)
        val voiceNames = listOf("ডিফল্ট ভয়েস") + voices.map {
            "${it.name} (${it.locale.displayName})"
        }
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames)
        val selectedIndex = voices.indexOfFirst { it.name == selectedVoiceName } + 1
        voiceSpinner.setSelection(selectedIndex.coerceAtLeast(0))
        layout.addView(voiceSpinner)

        fun addSlider(title: String, initial: Float): SeekBar {
            layout.addView(label("$title: ${"%.1f".format(Locale.US, initial)}", 13f, Color.WHITE))
            val valueLabel = layout.getChildAt(layout.childCount - 1) as TextView
            val seek = SeekBar(this).apply {
                max = 19
                progress = ((initial - 0.5f) * 10).toInt().coerceIn(0, 19)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueLabel.text = "$title: ${"%.1f".format(Locale.US, 0.5f + progress / 10f)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layout.addView(seek)
            return seek
        }

        val rateSeek = addSlider("কথার গতি", speechRate)
        val pitchSeek = addSlider("পিচ", speechPitch)

        AlertDialog.Builder(this)
            .setTitle("🎚 Voice Studio")
            .setView(ScrollView(this).apply { addView(layout) })
            .setNegativeButton("বন্ধ", null)
            .setNeutralButton("ভয়েস পরীক্ষা") { _, _ ->
                val text = if (speechLanguage == "bn-BD") "হ্যালো! আমি তোমার AI Companion।"
                    else "Hello! I am your AI companion."
                speak(text)
            }
            .setPositiveButton("সেভ") { _, _ ->
                speechLanguage = languages[languageSpinner.selectedItemPosition].second
                selectedVoiceName = if (voiceSpinner.selectedItemPosition == 0) ""
                    else voices.getOrNull(voiceSpinner.selectedItemPosition - 1)?.name ?: ""
                speechRate = 0.5f + rateSeek.progress / 10f
                speechPitch = 0.5f + pitchSeek.progress / 10f
                saveVoiceSettings()
                applyVoiceSettings()
                Toast.makeText(this, "Voice Settings সংরক্ষণ হয়েছে", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun applyVoiceSettings() {
        val tts = textToSpeech ?: return
        val locale = if (speechLanguage == "bn-BD") Locale("bn", "BD")
            else if (speechLanguage == "en-GB") Locale.UK else Locale.US
        tts.setLanguage(locale)
        tts.setSpeechRate(speechRate)
        tts.setPitch(speechPitch)
        if (selectedVoiceName.isNotBlank()) {
            val voice = tts.voices?.firstOrNull { it.name == selectedVoiceName }
            if (voice != null) tts.voice = voice
        }
    }

    private fun requestVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 501)
            return
        }
        startVoiceInput()
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "এই ফোনে Speech Recognition পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Toast.makeText(this@MainActivity, "কথা বোঝা যায়নি। আবার চেষ্টা করো।", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (spoken.isNotBlank()) {
                        input.setText(spoken)
                        input.setSelection(input.text.length)
                        if (callMode) sendMessage()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice সমস্যা: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(this, "Voice engine প্রস্তুত হচ্ছে। একটু পরে চেষ্টা করো।", Toast.LENGTH_SHORT).show()
            return
        }
        applyVoiceSettings()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "personal-ai-reply")
    }

    private fun renderHistory() {
        messagesBox.removeAllViews()
        val current = conversations.firstOrNull { it.id == activeId }
        if (current == null || current.messages.isEmpty()) {
            addBubble(false, "স্বাগতম! আমি তোমার Online AI Companion। কী নিয়ে কথা বলতে চাও?", -1)
        } else {
            current.messages.forEachIndexed { index, item ->
                addBubble(item.fromUser, item.text, index)
            }
        }
        updateChatTitle()
    }

    private fun addBubble(fromUser: Boolean, message: String, messageIndex: Int) {
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.RIGHT else Gravity.LEFT
        }
        val bubble = label(message, 14f, Color.WHITE).apply {
            setPadding(dp(13), dp(11), dp(13), dp(11))
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            background = rounded(
                if (fromUser) Color.rgb(78, 61, 139) else Color.rgb(36, 40, 62), 16
            )
            if (messageIndex >= 0) setOnLongClickListener {
                showMessageActions(messageIndex)
                true
            }
        }
        row.addView(bubble)
        messagesBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })
    }

    private fun showMessageActions(index: Int) {
        val item = history.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle("মেসেজ")
            .setItems(arrayOf("কপি করো", "ডিলিট করো")) { _, which ->
                if (which == 0) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", item.text))
                    Toast.makeText(this, "মেসেজ কপি হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    history.removeAt(index)
                    persist()
                    renderHistory()
                }
            }.show()
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        val current = conversations.firstOrNull { it.id == activeId } ?: return
        val conversationId = current.id
        val userMessage = ChatMessage(System.currentTimeMillis(), message, true)
        current.messages.add(userMessage)
        current.updatedAt = System.currentTimeMillis()
        if (current.title == "নতুন চ্যাট") current.title = message.take(32)
        persist()
        renderHistory()
        input.setText("")
        input.isEnabled = false
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        addBubble(false, "উত্তর তৈরি হচ্ছে...", -1)
        val previousHistory = current.messages.dropLast(1).toList()

        Thread {
            val answer = OnlineAiEngine().reply(message, previousHistory)
            runOnUiThread {
                val target = conversations.firstOrNull { it.id == conversationId }
                if (target != null) {
                    target.messages.add(ChatMessage(System.currentTimeMillis(), answer, false))
                    target.updatedAt = System.currentTimeMillis()
                    persist()
                }
                if (activeId == conversationId) {
                    renderHistory()
                    lastAiText = answer
                    if (callMode) speak(answer)
                }
                input.isEnabled = true
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            avatarView.setImageURI(uri)
            avatarView.clearColorFilter()
            getSharedPreferences("avatar", Context.MODE_PRIVATE).edit()
                .putString("uri", uri.toString()).apply()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 501 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startVoiceInput()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
