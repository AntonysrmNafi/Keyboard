package com.privacykeyboard.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// Home screen for BlockVeil Keyboard: shows enable/active status, buttons to
// enable and switch to the keyboard, and quick tiles for Settings and About.
// No network calls, no data collection, nothing is stored by this screen.
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.enable_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.select_button).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        findViewById<LinearLayout>(R.id.settings_tile).setOnClickListener {
            startActivity(Intent(this, SettingsMenuActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.about_tile).setOnClickListener {
            startActivity(
                Intent(this, InfoActivity::class.java)
                    .putExtra(InfoActivity.EXTRA_TITLE, "About")
                    .putExtra(InfoActivity.EXTRA_BODY, InfoActivity.ABOUT_TEXT)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabledIds = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        val isEnabled = enabledIds?.contains(packageName) == true

        findViewById<TextView>(R.id.status_dot_text).text = if (isEnabled) "\u25CF Active" else "\u25CB Inactive"
        findViewById<TextView>(R.id.status_text).text = if (isEnabled) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }
    }
}
