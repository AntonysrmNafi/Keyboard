package com.blockveil.keyboard

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
        // Point 3: prevent forced IME hide+reshow flash - this screen is opened
        // directly from the keyboard's own settings icon.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
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
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // Theme and Customize are placeholders for now, no functionality yet.
        findViewById<LinearLayout>(R.id.theme_tile).setOnClickListener {
            android.widget.Toast.makeText(this, getString(R.string.coming_soon), android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.customize_tile).setOnClickListener {
            android.widget.Toast.makeText(this, getString(R.string.coming_soon), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Point 3: opened directly from the keyboard's own settings gear icon -
        // push the real Settings menu on top, exactly like tapping the
        // settings_tile above does normally. Not finishing MainActivity here:
        // it stays as the safe, properly-established root of this task: pressing
        // back from Settings simply shows this screen for a moment, then a
        // second back exits normally, same as any other Android app.
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            startActivity(Intent(this, SettingsMenuActivity::class.java))
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

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}
