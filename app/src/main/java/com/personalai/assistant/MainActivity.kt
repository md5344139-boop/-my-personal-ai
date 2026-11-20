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

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var callMode = false
    private var lastAiText = ""

    private val history = mutableListOf<ChatMessage>()

    private val bg = Color.rgb(13, 15, 27)
    private val panel = Color.rgb(27, 30, 49)
    private val muted = Color.rgb(157, 164, 190)
    private val accent = Color.rgb(157, 105, 235)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = ChatStore(this)
        history.addAll(store.load())

        window.statusBarColor = bg
        window.navigationBarColor = bg

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("bn", "BD"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

                if (!ttsReady) {
                    textToSpeech?.setLanguage(Locale.US)
                    ttsReady = true
                }
            }
        }

        buildScreen()
        loadSavedAvatar()
        renderHistory()
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

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        heading.addView(label("My Personal AI", 21f, Color.WHITE, true))
        heading.addView(label("ONLINE AI COMPANION", 10f, muted, true))

        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))

        val callButton = label("VIDEO CALL", 10f, Color.WHITE, true).apply {
            setPadding(dp(12), dp(11), dp(12), dp(11))
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

        avatarPanel.addView(
            avatarView,
            LinearLayout.LayoutParams(dp(76), dp(76))
        )

        val avatarInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        avatarInfo.addView(
            label("Your AI Companion", 15f, Color.WHITE, true)
        )
        avatarInfo.addView(
            label("Online • Ready to talk", 11f, Color.rgb(105, 224, 181))
        )

        val changeButton = label("ছবি বদলাও", 11f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { chooseAvatar() }
        }
        avatarInfo.addView(changeButton)

        avatarPanel.addView(
            avatarInfo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        root.addView(
            avatarPanel,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        )

        root.addView(
            label("CONVERSATION", 10f, muted, true).apply {
                setPadding(dp(2), dp(4), 0, dp(8))
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

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

        val micButton = label("🎙", 19f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { requestVoiceInput() }
        }
        composer.addView(
            micButton,
            LinearLayout.LayoutParams(dp(42), dp(46))
        )

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

        val send = label("➤", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 30)
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(composer)

        val speakButton = label("🔊  শেষ উত্তরটি ভয়েসে শোনো", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(8))
            setOnClickListener {
                if (lastAiText.isNotBlank()) speak(lastAiText)
                else Toast.makeText(
                    this@MainActivity,
                    "এখনো কোনো AI উত্তর নেই",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        root.addView(speakButton)
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
        } catch (_: Exception) {
            // The user can select another image if the saved file is unavailable.
        }
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

        content.addView(
            callAvatar,
            LinearLayout.LayoutParams(dp(210), dp(250)).apply {
                bottomMargin = dp(18)
            }
        )

        content.addView(
            label("তোমার AI Companion", 18f, Color.WHITE, true)
        )
        content.addView(
            label("অনলাইন ভয়েস কথোপকথন", 12f, Color.rgb(105, 224, 181)).apply {
                setPadding(0, dp(7), 0, dp(20))
            }
        )

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        val talk = label("🎙 কথা বলো", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = rounded(accent, 24)
            setOnClickListener { requestVoiceInput() }
        }

        val end = label("কল শেষ", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = rounded(Color.rgb(170, 48, 68), 24)
            setOnClickListener { dialog.dismiss() }
        }

        controls.addView(talk)
        controls.addView(
            end,
            LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(12)
            }
        )

        content.addView(controls)
        dialog.setOnDismissListener { callMode = false }
        dialog.show()
    }

    private fun requestVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                501
            )
            return
        }
        startVoiceInput()
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "এই ফোনে Speech Recognition পাওয়া যায়নি",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Toast.makeText(
                            this@MainActivity,
                            "কথা বোঝা যায়নি। আবার চেষ্টা করো।",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onResults(results: Bundle?) {
                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )?.firstOrNull().orEmpty()

                        if (spoken.isNotBlank()) {
                            input.setText(spoken)
                            input.setSelection(input.text.length)
                            if (callMode) sendMessage()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
            )

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Voice সমস্যা: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(
                this,
                "Voice engine প্রস্তুত হচ্ছে। একটু পরে চেষ্টা করো।",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "personal-ai-reply"
        )
    }

    private fun renderHistory() {
        messagesBox.removeAllViews()

        if (history.isEmpty()) {
            addBubble(
                false,
                "স্বাগতম! আমি তোমার Online AI Companion। কী নিয়ে কথা বলতে চাও?"
            )
        } else {
            history.forEach { addBubble(it.fromUser, it.text) }
        }
    }

    private fun addBubble(fromUser: Boolean, message: String) {
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.RIGHT else Gravity.LEFT
        }

        val bubble = label(message, 14f, Color.WHITE).apply {
            setPadding(dp(13), dp(11), dp(13), dp(11))
            maxWidth =
                (resources.displayMetrics.widthPixels * 0.82f).toInt()
            background = rounded(
                if (fromUser) Color.rgb(78, 61, 139)
                else Color.rgb(36, 40, 62),
                16
            )
        }

        row.addView(bubble)
        messagesBox.addView(
            row,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return

        val userMessage = ChatMessage(
            System.currentTimeMillis(),
            message,
            true
        )

        history.add(userMessage)
        store.save(history)
        addBubble(true, message)

        input.setText("")
        input.isEnabled = false

        (getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        addBubble(false, "উত্তর তৈরি হচ্ছে...")

        val previousHistory = history.dropLast(1).toList()

        Thread {
            val answer = OnlineAiEngine().reply(message, previousHistory)

            runOnUiThread {
                if (messagesBox.childCount > 0) {
                    messagesBox.removeViewAt(messagesBox.childCount - 1)
                }

                val reply = ChatMessage(
                    System.currentTimeMillis(),
                    answer,
                    false
                )

                history.add(reply)
                store.save(history)
                addBubble(false, answer)
                lastAiText = answer
                input.isEnabled = true

                if (callMode) speak(answer)
            }
        }.start()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            avatarView.setImageURI(uri)
            avatarView.clearColorFilter()

            getSharedPreferences("avatar", Context.MODE_PRIVATE)
                .edit()
                .putString("uri", uri.toString())
                .apply()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 501 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
