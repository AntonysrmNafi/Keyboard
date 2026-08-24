package com.privacykeyboard.app

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Point 1 / 1.2: dedicated FAQ screen (previously just a giant plain-text blob
// shown through InfoActivity). Questions are now bold, white, and a couple
// points bigger than the answer text, and a search box at the top filters the
// list live by question or answer text - fully offline, no network involved.
class FaqActivity : Activity() {

    private lateinit var container: LinearLayout
    private val colorTextPrimary by lazy { resources.getColor(R.color.text_primary) }
    private val colorTextSecondary by lazy { resources.getColor(R.color.text_secondary) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)

        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }
        container = findViewById(R.id.row_container)

        renderList(FAQ_ITEMS)

        findViewById<EditText>(R.id.faq_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                val filtered = if (query.isEmpty()) {
                    FAQ_ITEMS
                } else {
                    FAQ_ITEMS.filter {
                        it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true)
                    }
                }
                renderList(filtered)
            }
        })
    }

    private fun renderList(items: List<Pair<String, String>>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No matching questions."
                setTextColor(colorTextSecondary)
                textSize = 14f
                setPadding(dp(20), dp(16), dp(20), dp(16))
            })
            return
        }

        items.forEach { (question, answer) ->
            container.addView(TextView(this).apply {
                text = question
                // Point 1: bold, white, ~2px bigger than the answer text below
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 16f
                setPadding(dp(20), dp(16), dp(20), dp(4))
                setTextIsSelectable(true)
            })
            container.addView(TextView(this).apply {
                text = answer
                setTextColor(colorTextSecondary)
                textSize = 14f
                setLineSpacing(dp(4).toFloat(), 1f)
                setPadding(dp(20), 0, dp(20), dp(8))
                setTextIsSelectable(true)
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        // Point 1.2: each entry is (question, answer) - used for both display
        // and search filtering.
        val FAQ_ITEMS: List<Pair<String, String>> = listOf(
            "Is BlockVeil Keyboard really offline?" to
                "Yes. The App does not request the INTERNET permission, so Android does not allow it to open any network connection at all, not even in the background.",
            "Does it collect or send anything I type?" to
                "No. Nothing you type is logged, collected, or transmitted anywhere. See Privacy Policy for full details.",
            "How do I enable and switch to BlockVeil Keyboard?" to
                "From the home screen, tap \"Enable Keyboard\" to open your system keyboard settings and turn it on, then tap \"Switch to BlockVeil Keyboard\" (or use your device's keyboard switch icon) while typing in any text field.",
            "How do I change typing modes?" to
                "Tap the \"Aa/অ\" key on the spacebar row, or swipe left/right on the spacebar, to cycle between English and প্রভাত (Bangla traditional).",
            "Why is Bangla Phonetic mode unavailable right now?" to
                "It is temporarily out of the mode-switching cycle while it is refined further. It has not been removed and will return in a future update.",
            "How do word suggestions work?" to
                "Suggestions come from a small offline dictionary bundled with the App, based on what you have typed so far. You can expand this list yourself in Settings > Dictionary.",
            "What is My Dictionary, and how is it different from the BlockVeil Dictionary?" to
                "BlockVeil Dictionary is the App's bundled, read-only word list. My Dictionary is your own personal word list, entirely separate from it. You can add, edit, and delete your own words from Settings > Dictionary > My Dictionary.",
            "What is Dictionary Lock?" to
                "An optional PIN you can set per language to keep your personal dictionary private. Once set, opening that language's My Dictionary asks for the PIN first. Only a secure hash of the PIN is ever stored, never the PIN itself.",
            "Where is my clipboard history stored, and can I clear it?" to
                "Locally on your device only. You can view and clear it anytime from Settings > Clipboard.",
            "How do I build or update the App myself?" to
                "The project builds through GitHub Actions. Push the repository to GitHub, run the \"Build APK\" workflow from the Actions tab, then download and install the generated APK."
        )
    }
}
