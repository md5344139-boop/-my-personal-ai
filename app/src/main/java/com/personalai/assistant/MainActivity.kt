package com.personalai.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.InputType

class MainActivity : Activity() {
    private lateinit var store: ChatStore
    private lateinit var messagesBox: LinearLayout
    private lateinit var input: EditText
    private lateinit var modeButton: TextView
    private var onlineMode = false
    private val history = mutableListOf<ChatMessage>()

    private val bg = Color.rgb(13, 15, 27)
    private val panel = Color.rgb(27, 30, 49)
    private val muted = Color.rgb(157, 164, 190)
    private val accent = Color.rgb(137, 105, 235)

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)
        history.addAll(store.load())
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildScreen()
        renderHistory()
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        setContentView(root)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }

        header.addView(label("✦", 28f, accent, true),
            LinearLayout.LayoutParams(dp(42), dp(48)))

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleColumn.addView(label("My Personal AI", 20f, Color.WHITE, true))
        titleColumn.addView(label("YOUR PRIVATE ASSISTANT", 9f, muted, true))
        header.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))

        modeButton = label("OFFLINE  ▾", 11f,
            Color.rgb(194, 177, 255), true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(44, 36, 70), 24)
            setOnClickListener { toggleMode() }
        }
        header.addView(modeButton)
        root.addView(header)

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(18), dp(14), dp(18))
            background = rounded(Color.rgb(25, 28, 47), 20)
        }

        val orb = TextView(this).apply {
            text = "✧"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(95, 66, 157),
                    Color.rgb(34, 102, 128))
            ).apply { shape = GradientDrawable.OVAL }
        }

        welcome.addView(orb, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            bottomMargin = dp(12)
        })
        welcome.addView(label("Your AI space starts here", 17f, Color.WHITE, true))
        welcome.addView(label("তোমার ব্যক্তিগত AI সহকারী", 12f, muted).apply {
            setPadding(0, dp(6), 0, 0)
        })

        root.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        root.addView(label("CONVERSATION", 10f, muted, true).apply {
            setPadding(dp(2), dp(5), 0, dp(8))
        })

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
            setPadding(dp(8), dp(5), dp(6), dp(5))
            background = rounded(panel, 18)
        }

        input = EditText(this).apply {
            hint = "এখানে মেসেজ লিখো..."
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 4
            background = null
            setPadding(dp(8), dp(8), dp(4), dp(8))
        }

        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        val send = label("➤", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 30)
            setOnClickListener { sendMessage() }
        }

        composer.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(composer)

        root.addView(label("PRIVATE • PERSONAL • V1.0.0",
            9f, Color.rgb(105, 112, 139), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun toggleMode() {
        onlineMode = !onlineMode

        modeButton.text =
            if (onlineMode) "ONLINE  ▾" else "OFFLINE  ▾"

        modeButton.setTextColor(
            if (onlineMode) Color.rgb(105, 224, 181)
            else Color.rgb(194, 177, 255)
        )

        Toast.makeText(
            this,
            if (onlineMode) "Online Gemini mode selected"
            else "Offline mode selected",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderHistory() {
        messagesBox.removeAllViews()

        if (history.isEmpty()) {
            addBubble(false,
                "স্বাগতম! তোমার ব্যক্তিগত AI সহকারী প্রস্তুত।")
        } else {
            history.forEach {
                addBubble(it.fromUser, it.text)
            }
        }
    }

    private fun addBubble(fromUser: Boolean, message: String) {
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.RIGHT else Gravity.LEFT
        }

        val bubble = label(message, 14f, Color.WHITE).apply {
            setPadding(dp(13), dp(11), dp(13), dp(11))
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            background = rounded(
                if (fromUser) Color.rgb(78, 61, 139)
                else Color.rgb(36, 40, 62),
                16
            )
        }

        row.addView(bubble)
        messagesBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return

        val useOnline = onlineMode

        val userMessage = ChatMessage(
            System.currentTimeMillis(), message, true
        )

        history.add(userMessage)
        store.save(history)
        addBubble(true, message)

        input.setText("")
        input.isEnabled = false

        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        addBubble(false, "উত্তর তৈরি হচ্ছে...")

        val engine: AiEngine =
            if (useOnline) OnlineAiEngine() else OfflineAiEngine()

        val previousHistory = history.dropLast(1).toList()

        Thread {
            val answer = engine.reply(message, previousHistory)

            runOnUiThread {
                if (messagesBox.childCount > 0) {
                    messagesBox.removeViewAt(messagesBox.childCount - 1)
                }

                val reply = ChatMessage(
                    System.currentTimeMillis(), answer, false
                )

                history.add(reply)
                addBubble(false, answer)
                store.save(history)

                input.isEnabled = true
            }
        }.start()
    }
}
