package com.blockveil.keyboard

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// Renders one settings section (Preferences / Appearance & Layouts / Text correction)
// as a list of switch rows. Every row reads and writes SettingsStore directly,
// so toggles take effect the next time the keyboard reads that key.
class SettingsSectionActivity : Activity() {

    // Point: only the "Show copied images on Clipboard" row needs special,
    // permission-gated handling (see buildRow below) - kept as a field so
    // onRequestPermissionsResult and onResume can both find and correct its
    // switch without rebuilding the whole screen.
    private var imagesSwitch: Switch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        val section = intent.getStringExtra(EXTRA_SECTION) ?: SettingsItems.SECTION_PREFERENCES
        val (title, items) = SettingsItems.forSection(section)

        findViewById<TextView>(R.id.section_title).text = title
        findViewById<android.widget.ImageView>(R.id.back_button).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.row_container)
        items.forEach { item -> container.addView(buildRow(item)) }
    }

    override fun onResume() {
        super.onResume()
        // Point: if "Show copied images on Clipboard" was left on but the
        // user revoked the permission from App Info since last time this
        // screen was shown, force it back off here (both the stored
        // preference and the switch) - the setting is only ever meant to be
        // on WHILE the permission is actually granted.
        val switch = imagesSwitch ?: return
        val stillOn = SettingsStore.getBoolean(this, SettingsStore.KEY_CLIPBOARD_SHOW_IMAGES, false)
        if (stillOn && !hasImagePermission()) {
            SettingsStore.setBoolean(this, SettingsStore.KEY_CLIPBOARD_SHOW_IMAGES, false)
            ScreenshotJobService.cancel(this)
            switch.isChecked = false
        }
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
        }

        if (item.key == SettingsStore.KEY_CLIPBOARD_SHOW_IMAGES) {
            // Point: this ONE row needs permission-gated behavior - turning
            // it on requires READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE, so it
            // checks first, requests if missing, and stays off until the
            // permission is actually granted. Turning it off never needs
            // permission and always just works. See ScreenshotJobService for
            // what actually watches for new screenshots once this is on.
            imagesSwitch = switch
            // Point: correct the switch to match reality on first build too
            // (not just onResume) - e.g. the permission was revoked between
            // app launches.
            if (switch.isChecked && !hasImagePermission()) {
                SettingsStore.setBoolean(this, SettingsStore.KEY_CLIPBOARD_SHOW_IMAGES, false)
                switch.isChecked = false
            }
            switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (hasImagePermission()) {
                        SettingsStore.setBoolean(this, item.key, true)
                        ScreenshotJobService.schedule(this)
                    } else {
                        // Point: don't let the switch visually flip on until
                        // permission is actually granted - onRequestPermissionsResult
                        // below flips it (and persists the setting) once it is.
                        switch.isChecked = false
                        requestPermissions(arrayOf(imagePermissionName()), IMAGE_PERMISSION_REQUEST_CODE)
                    }
                } else {
                    SettingsStore.setBoolean(this, item.key, false)
                    ScreenshotJobService.cancel(this)
                }
            }
        } else {
            switch.setOnCheckedChangeListener { _, isChecked ->
                SettingsStore.setBoolean(context, item.key, isChecked)
            }
        }

        row.addView(textColumn)
        row.addView(switch)
        return row
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != IMAGE_PERMISSION_REQUEST_CODE) return
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            SettingsStore.setBoolean(this, SettingsStore.KEY_CLIPBOARD_SHOW_IMAGES, true)
            ScreenshotJobService.schedule(this)
            imagesSwitch?.isChecked = true
        }
        // Point: denied -> leave the setting and switch off (they're already
        // off from the click handler above) - no error dialog, the switch
        // simply stays where it was.
    }

    private fun imagePermissionName(): String = if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun hasImagePermission(): Boolean =
        checkSelfPermission(imagePermissionName()) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SECTION = "section"
        private const val IMAGE_PERMISSION_REQUEST_CODE = 4301
    }
}
