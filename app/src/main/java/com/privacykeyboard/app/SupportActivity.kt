package com.privacykeyboard.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Support menu: FAQ is fully wired to a static offline answer sheet; Suggest Feature
// and Report Bug are placeholders for now (no network access, so nothing gets sent
// anywhere yet - both just show "Coming soon").
class SupportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        findViewById<TextView>(R.id.section_title).text = "Support"
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.row_container)

        container.addView(buildRow("FAQ", "Answers to common questions") {
            startActivity(
                Intent(this, InfoActivity::class.java)
                    .putExtra(InfoActivity.EXTRA_TITLE, "FAQ")
                    .putExtra(InfoActivity.EXTRA_BODY, InfoActivity.FAQ_TEXT)
            )
        })
        container.addView(buildRow("Suggest Feature", "Tell us what you'd like to see next") {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        })
        container.addView(buildRow("Report Bug", "Let us know if something's not working") {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        })
    }

    private fun buildRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
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
        row.addView(TextView(this).apply {
            text = subtitle
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
        })
        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
