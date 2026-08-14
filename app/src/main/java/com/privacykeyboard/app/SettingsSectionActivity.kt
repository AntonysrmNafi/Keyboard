package com.privacykeyboard.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// Renders one settings section (Preferences / Appearance & Layouts / Text correction)
// as a list of switch rows. Every row reads and writes SettingsStore directly,
// so toggles take effect the next time the keyboard reads that key.
class SettingsSectionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        val section = intent.getStringExtra(EXTRA_SECTION) ?: SettingsItems.SECTION_PREFERENCES
        val (title, items) = SettingsItems.forSection(section)

        findViewById<TextView>(R.id.section_title).text = title
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.row_container)
        items.forEach { item -> container.addView(buildRow(item)) }
    }

    private fun buildRow(item: ToggleItem): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = item.title
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
        }
        val subtitleView = TextView(this).apply {
            text = item.subtitle
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
        }
        textColumn.addView(titleView)
        textColumn.addView(subtitleView)

        val switch = Switch(this).apply {
            isChecked = SettingsStore.getBoolean(context, item.key, item.default)
            setOnCheckedChangeListener { _, isChecked ->
                SettingsStore.setBoolean(context, item.key, isChecked)
            }
        }

        row.addView(textColumn)
        row.addView(switch)
        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SECTION = "section"
    }
}
