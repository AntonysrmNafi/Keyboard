package com.privacykeyboard.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

// Advanced screen with settings that need a value picker rather than a simple
// on/off switch. Currently just Space cursor speed; more will land here as
// more of the keyboard becomes configurable.
class AdvancedSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        findViewById<TextView>(R.id.section_title).text = "Advanced"
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.row_container)
        container.addView(buildSpeedRow())
        container.addView(buildNoteRow())
    }

    private fun buildSpeedRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            isFocusable = true
        }

        val titleView = TextView(this).apply {
            text = "Space cursor speed"
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
        }
        val valueView = TextView(this).apply {
            text = speedLabel(currentSpeed())
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
        }

        row.addView(titleView)
        row.addView(valueView)
        row.setOnClickListener { showSpeedPicker(valueView) }
        return row
    }

    private fun buildNoteRow(): TextView {
        return TextView(this).apply {
            text = "More advanced options (long-press delay, keypress sound volume tuning) are not implemented in this version yet."
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
    }

    private fun showSpeedPicker(valueView: TextView) {
        val labels = arrayOf("Slow", "Default", "Fast")
        AlertDialog.Builder(this)
            .setTitle("Space cursor speed")
            .setSingleChoiceItems(labels, currentSpeed()) { dialog, which ->
                SettingsStore.setInt(this, SettingsStore.KEY_SPACE_CURSOR_SPEED, which)
                valueView.text = speedLabel(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun currentSpeed(): Int = SettingsStore.getInt(this, SettingsStore.KEY_SPACE_CURSOR_SPEED, 1)

    private fun speedLabel(level: Int): String = when (level) {
        0 -> "Slow"
        2 -> "Fast"
        else -> "Default"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
