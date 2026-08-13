package com.privacykeyboard.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView

// Launcher screen for BlockVeil Keyboard. Only lets the user open system settings
// to enable the keyboard, and open the keyboard picker to switch to it.
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
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val statusView = findViewById<TextView>(R.id.status_text)
        val enabledIds = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        val isEnabled = enabledIds?.contains(packageName) == true
        statusView.text = if (isEnabled) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }
    }
}
