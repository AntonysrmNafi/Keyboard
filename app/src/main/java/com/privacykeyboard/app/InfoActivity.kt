package com.privacykeyboard.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

// Static, read-only screen. No settings, no network, just text.
class InfoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        findViewById<TextView>(R.id.info_title).text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        findViewById<TextView>(R.id.info_body).text = intent.getStringExtra(EXTRA_BODY) ?: ""
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"

        const val RELEASE_NOTES = """Version 1.1
- Space cursor: swipe the spacebar left/right to move the text cursor, speed adjustable in Advanced
- Double-space tab option added alongside double-space period

Version 1.0
- First release
- English, Bangla phonetic and Bangla traditional typing modes
- Offline suggestion bar
- Auto-capitalization, double-space period, vibrate/sound/popup on keypress
- Optional number row"""

        const val ABOUT_TEXT = """BlockVeil Keyboard

A fully offline Android keyboard. No INTERNET permission is requested, so the app cannot send anything anywhere. No keystrokes are logged. Word suggestions come only from a small local word list bundled with the app.

Some features from other keyboard apps are intentionally left out because they require an internet connection (GIF search, personalized suggestions that learn from typed data, cloud voice typing, downloadable language packs) which would break the offline-only design of this app."""
    }
}
