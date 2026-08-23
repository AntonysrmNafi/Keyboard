package com.privacykeyboard.app

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Rich About screen: a professional description of the app, then quick-access
// buttons to Open Source info, Privacy Policy, Terms & Conditions and Merchandise,
// finishing with the version/copyright footer.
class AboutActivity : Activity() {

    private val colorCard by lazy { resources.getColor(R.color.keyboard_bg) }
    private val colorTextPrimary by lazy { resources.getColor(R.color.text_primary) }
    private val colorTextSecondary by lazy { resources.getColor(R.color.text_secondary) }
    private val colorAccent by lazy { resources.getColor(R.color.accent_mint) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.about_container)

        container.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(colorTextPrimary)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(4), dp(20), dp(6))
        })

        container.addView(TextView(this).apply {
            text = InfoActivity.ABOUT_TEXT
            setTextColor(colorTextSecondary)
            textSize = 14f
            setLineSpacing(dp(6).toFloat(), 1f)
            setPadding(dp(20), 0, dp(20), dp(20))
        })

        container.addView(buildAboutButton("\uD83C\uDF10", "BlockVeil Keyboard is open source!") {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        })
        container.addView(buildAboutButton("\uD83D\uDD12", "Privacy Policy") {
            startActivity(
                Intent(this, InfoActivity::class.java)
                    .putExtra(InfoActivity.EXTRA_TITLE, "Privacy Policy")
                    .putExtra(InfoActivity.EXTRA_BODY, InfoActivity.PRIVACY_POLICY)
            )
        })
        container.addView(buildAboutButton("\uD83D\uDCC4", "Terms & Conditions") {
            startActivity(
                Intent(this, InfoActivity::class.java)
                    .putExtra(InfoActivity.EXTRA_TITLE, "Terms & Conditions")
                    .putExtra(InfoActivity.EXTRA_BODY, InfoActivity.TERMS_AND_CONDITIONS)
            )
        })
        container.addView(buildAboutButton("\uD83D\uDECD\uFE0F", "Merchandise") {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        })

        container.addView(TextView(this).apply {
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
            text = "Version $versionName\n\u00A9 2026 BlockVeil"
            setTextColor(colorTextSecondary)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(4))
        })
    }

    private fun buildAboutButton(icon: String, title: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardDrawable(colorCard, 14f)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(10)
            }
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 17f
            setPadding(0, 0, dp(14), 0)
        })
        row.addView(TextView(this).apply {
            text = title
            setTextColor(colorTextPrimary)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            // Point 2: Enable text selection for long press
            setTextIsSelectable(true)
        })
        row.addView(TextView(this).apply {
            text = "\u203A"
            setTextColor(colorAccent)
            textSize = 18f
        })
        return row
    }

    private fun cardDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
