package com.privacykeyboard.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

// Top-level settings menu: Preferences, Appearance & Layouts, Text correction,
// Advanced, Release notes, About. Matches the section grouping style of
// well-known keyboard apps, trimmed to only what BlockVeil actually implements.
class SettingsMenuActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Point: this screen is now opened directly from the keyboard's own
        // settings icon, so prevent the forced IME hide+reshow flash.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        setContentView(R.layout.activity_settings_menu)

        findViewById<android.widget.ImageView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.menu_container)

        container.addView(buildMenuRow("Preferences", "Typing behavior and keypress feedback") {
            openSection(SettingsItems.SECTION_PREFERENCES)
        })
        container.addView(buildMenuRow("Appearance & Layouts", "Number row, sizing, one-handed and more") {
            startActivity(Intent(this, AppearanceSettingsActivity::class.java))
        })
        container.addView(buildMenuRow("Text correction", "Suggestions and word filtering") {
            openSection(SettingsItems.SECTION_TEXT_CORRECTION)
        })
        container.addView(buildMenuRow("Dictionary", "Browse the offline dictionary, manage your own words") {
            startActivity(Intent(this, DictionarySettingsActivity::class.java))
        })
        container.addView(buildMenuRow("Clipboard", "View and clear copied text history") {
            startActivity(Intent(this, ClipboardSettingsActivity::class.java))
        })
        container.addView(buildMenuRow("Advanced", "Fine-tuning options") {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        })
        container.addView(buildMenuRow("Support", "FAQ, feature suggestions, bug reports") {
            startActivity(Intent(this, SupportActivity::class.java))
        })
        container.addView(buildMenuRow("Release notes", null) {
            openInfo("Release notes", InfoActivity.RELEASE_NOTES)
        })
        container.addView(buildMenuRow("About", "About BlockVeil Keyboard, Privacy Policy, Terms & Conditions") {
            startActivity(Intent(this, AboutActivity::class.java))
        })
    }

    private fun openSection(section: String) {
        startActivity(
            Intent(this, SettingsSectionActivity::class.java)
                .putExtra(SettingsSectionActivity.EXTRA_SECTION, section)
        )
    }

    private fun openInfo(title: String, body: String) {
        startActivity(
            Intent(this, InfoActivity::class.java)
                .putExtra(InfoActivity.EXTRA_TITLE, title)
                .putExtra(InfoActivity.EXTRA_BODY, body)
        )
    }

    private fun buildMenuRow(title: String, subtitle: String?, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }

        row.addView(TextView(this).apply {
            text = title
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 16f
        })

        if (subtitle != null) {
            row.addView(TextView(this).apply {
                text = subtitle
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 12f
            })
        }

        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
