package com.privacykeyboard.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class AppearanceSettingsActivity : Activity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        findViewById<TextView>(R.id.section_title).text = "Appearance & Layouts"
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        container = findViewById(R.id.row_container)

        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_ENABLE_NUMBER_ROW,
                "Enable number row",
                "Adds an extra row",
                true
            )
        )
        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_LARGE_NUMBER_ROW,
                "Large number row",
                "Makes the number row taller and easier to hit",
                false
            )
        )
        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_HIDE_LONG_PRESS_HINTS,
                "Hide long press hints",
                "Hide the small number labels on the top letter row (long-press still works when shown)",
                false
            )
        )
        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_SPACE_CURSOR_ENABLED,
                "Space cursor",
                "Swipe left or right on the spacebar to move the text cursor",
                true
            )
        )

        val resizeSwitch = buildSwitchRow(
            SettingsStore.KEY_KEYBOARD_RESIZE_ENABLED,
            "Enable keyboard resizing",
            "",
            false
        )
        container.addView(resizeSwitch)

        val heightPortraitRow = buildPercentRow(
            "Keyboard Height (Portrait)",
            SettingsStore.KEY_KEYBOARD_HEIGHT_PORTRAIT,
            80,
            intArrayOf(60, 70, 80, 90, 100)
        )
        val heightLandscapeRow = buildPercentRow(
            "Keyboard Height (Landscape)",
            SettingsStore.KEY_KEYBOARD_HEIGHT_LANDSCAPE,
            80,
            intArrayOf(60, 70, 80, 90, 100)
        )
        container.addView(heightPortraitRow)
        container.addView(heightLandscapeRow)

        val resizeEnabled = SettingsStore.getBoolean(this, SettingsStore.KEY_KEYBOARD_RESIZE_ENABLED, false)
        setRowEnabled(heightPortraitRow, resizeEnabled)
        setRowEnabled(heightLandscapeRow, resizeEnabled)

        val resizeSwitchWidget = resizeSwitch.findViewWithTag<Switch>("switch")
        resizeSwitchWidget?.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setBoolean(this, SettingsStore.KEY_KEYBOARD_RESIZE_ENABLED, isChecked)
            setRowEnabled(heightPortraitRow, isChecked)
            setRowEnabled(heightLandscapeRow, isChecked)
        }

        container.addView(
            buildPercentRow(
                "One Handed Keyboard Width (Portrait)",
                SettingsStore.KEY_ONE_HANDED_WIDTH_PORTRAIT,
                85,
                intArrayOf(50, 60, 70, 80, 85, 90, 100)
            )
        )
        container.addView(
            buildPercentRow(
                "One Handed Keyboard Width (Landscape)",
                SettingsStore.KEY_ONE_HANDED_WIDTH_LANDSCAPE,
                40,
                intArrayOf(20, 30, 40, 50, 60, 70)
            )
        )
        container.addView(buildNoteRow("Tap the one-hand icon above the keyboard to turn one-handed mode on and cycle left/right."))

        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_ENABLE_SPLIT_KEYBOARD,
                "Enable split keyboard",
                "Not built yet: a real split needs separate left/right layouts for every typing mode. Saved here, applied in a future update.",
                false
            )
        )
        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_ENABLE_SPLIT_KEYBOARD_FOLDABLE,
                "Enable split keyboard for foldable phone",
                "Same as above, for foldable screens. Not built yet.",
                false
            )
        )
        container.addView(
            buildSwitchRow(
                SettingsStore.KEY_FORCED_ENTER_BUTTON,
                "Forced enter button",
                "Always show a plain enter arrow, never an emoji, on the last key",
                true
            )
        )
    }

    private fun buildSwitchRow(key: String, title: String, subtitle: String, default: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = title
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
        })
        if (subtitle.isNotEmpty()) {
            textColumn.addView(TextView(this).apply {
                text = subtitle
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 12f
            })
        }

        val switch = Switch(this).apply {
            tag = "switch"
            isChecked = SettingsStore.getBoolean(context, key, default)
            setOnCheckedChangeListener { _, isChecked ->
                SettingsStore.setBoolean(context, key, isChecked)
            }
        }

        row.addView(textColumn)
        row.addView(switch)
        return row
    }

    private fun buildPercentRow(title: String, key: String, default: Int, options: IntArray): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            isFocusable = true
        }

        row.addView(TextView(this).apply {
            text = title
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
            tag = "title"
        })
        val valueView = TextView(this).apply {
            text = "${SettingsStore.getInt(this@AppearanceSettingsActivity, key, default)}%"
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
            tag = "value"
        }
        row.addView(valueView)

        row.setOnClickListener {
            if (!row.isEnabled) return@setOnClickListener
            val labels = options.map { "$it%" }.toTypedArray()
            val current = SettingsStore.getInt(this, key, default)
            val currentIndex = options.indexOf(current).let { if (it < 0) options.indexOf(default) else it }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    SettingsStore.setInt(this, key, options[which])
                    valueView.text = "${options[which]}%"
                    dialog.dismiss()
                }
                .show()
        }
        return row
    }

    private fun buildNoteRow(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dp(20), dp(4), dp(20), dp(16))
        }
    }

    private fun setRowEnabled(row: LinearLayout, enabled: Boolean) {
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else 0.4f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
