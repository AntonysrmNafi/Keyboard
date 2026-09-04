package com.blockveil.keyboard

import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView

class ClipboardSettingsActivity : Activity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        findViewById<TextView>(R.id.section_title).text = "Clipboard"
        findViewById<android.widget.ImageView>(R.id.back_button).setOnClickListener { finish() }

        container = findViewById(R.id.row_container)
        refresh()
    }

    private fun refresh() {
        container.removeAllViews()

        val items = ClipboardStore.getItems(this)

        container.addView(TextView(this).apply {
            text = "History is stored only on this phone and is never sent anywhere."
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dp(20), dp(16), dp(20), dp(8))
        })

        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Nothing copied yet."
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 14f
                setPadding(dp(20), dp(12), dp(20), dp(12))
            })
            return
        }

        items.forEach { item ->
            val displayText = when (item.type) {
                "text" -> item.text ?: "(empty)"
                "image" -> "\uD83D\uDCCE Image (${item.imageBase64?.length?.div(1000) ?: 0}KB)"
                else -> "(unknown)"
            }
            container.addView(TextView(this).apply {
                text = displayText
                setTextColor(resources.getColor(R.color.text_primary))
                textSize = 14f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnLongClickListener {
                    ClipboardStore.removeItem(this@ClipboardSettingsActivity, item.id)
                    refresh()
                    true
                }
            })
        }

        container.addView(TextView(this).apply {
            text = "Clear all"
            setTextColor(resources.getColor(R.color.accent_mint))
            textSize = 14f
            setPadding(dp(20), dp(20), dp(20), dp(16))
            setOnClickListener {
                ClipboardStore.clear(this@ClipboardSettingsActivity)
                refresh()
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
