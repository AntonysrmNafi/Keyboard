package com.blockveil.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// Advanced screen with settings that need a value picker rather than a simple
// on/off switch. Currently just Space swipe sensitivity; more will land here as
// more of the keyboard becomes configurable.
class AdvancedSettingsActivity : Activity() {

    private lateinit var mediaSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        findViewById<TextView>(R.id.section_title).text = "Advanced"
        findViewById<android.widget.ImageView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.row_container)
        container.addView(buildVolumeKeyCursorRow())
        container.addView(buildVolumeKeyCursorMediaRow())
        container.addView(buildSpeedRow())
        container.addView(buildNoteRow())
    }

    // Point 4.1: lets the user move the text cursor with the volume buttons
    // while typing. Defaults to on.
    private fun buildVolumeKeyCursorRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = "Enable Volume Key Cursor"
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
        })
        textColumn.addView(TextView(this).apply {
            text = "Move the cursor with the volume buttons while typing. Does not affect volume during calls, audio, or video."
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
        })

        val switch = Switch(this).apply {
            isChecked = SettingsStore.getBoolean(context, SettingsStore.KEY_VOLUME_KEY_CURSOR, true)
            setOnCheckedChangeListener { _, isChecked ->
                SettingsStore.setBoolean(context, SettingsStore.KEY_VOLUME_KEY_CURSOR, isChecked)
                // Point 4.2: turning this off auto-disables the calls/media variant too,
                // since that one only makes sense when this is on.
                if (!isChecked && mediaSwitch.isChecked) {
                    mediaSwitch.isChecked = false
                    SettingsStore.setBoolean(this@AdvancedSettingsActivity, SettingsStore.KEY_VOLUME_KEY_CURSOR_MEDIA, false)
                }
                mediaSwitch.isEnabled = isChecked
                mediaSwitch.alpha = if (isChecked) 1f else 0.4f
            }
        }

        row.addView(textColumn)
        row.addView(switch)
        return row
    }

    // Point 4.2: extends volume-key cursor control to work even during calls,
    // audio, and video playback (instead of adjusting the volume in those
    // moments). Only meaningful, and only enable-able, while 4.1 is on.
    private fun buildVolumeKeyCursorMediaRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = "Enable Cursor during calls, Audio & Video"
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
        })
        textColumn.addView(TextView(this).apply {
            text = "Volume buttons move the cursor even during calls or while playing audio/video, instead of changing the volume. Requires Enable Volume Key Cursor to be on."
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
        })

        val volumeCursorOn = SettingsStore.getBoolean(this, SettingsStore.KEY_VOLUME_KEY_CURSOR, true)
        mediaSwitch = Switch(this).apply {
            isChecked = volumeCursorOn && SettingsStore.getBoolean(context, SettingsStore.KEY_VOLUME_KEY_CURSOR_MEDIA, false)
            isEnabled = volumeCursorOn
            alpha = if (volumeCursorOn) 1f else 0.4f
            setOnCheckedChangeListener { _, isChecked ->
                SettingsStore.setBoolean(context, SettingsStore.KEY_VOLUME_KEY_CURSOR_MEDIA, isChecked)
            }
        }

        row.addView(textColumn)
        row.addView(mediaSwitch)
        return row
    }

    private fun buildSpeedRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            isFocusable = true
        }

        val titleView = TextView(this).apply {
            text = "Space swipe sensitivity"
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
            .setTitle("Space swipe sensitivity")
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
