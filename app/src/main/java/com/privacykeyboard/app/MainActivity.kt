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
        // Point 3: prevent forced IME hide+reshow flash - this screen is opened
        // directly from the keyboard's own settings icon.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        setContentView(R.layout.activity_main)

        // Point 4: the keyboard service can't show a permission dialog itself, so
        // request the image-read permission here (needed only to notice newly
        // taken screenshots for the on-device clipboard history).
        requestImagePermissionIfNeeded()

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
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestImagePermissionIfNeeded() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
        }
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
