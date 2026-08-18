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

        const val RELEASE_NOTES = """Version 1
- First release
- English, Bangla phonetic (temporarily unavailable) and প্রভাত (Bangla traditional) typing modes
- Two-page symbols/numbers keyboard and a calculator-style numpad
- Offline suggestion bar with a small local dictionary
- Auto-capitalization, double-space period/tab, vibrate/sound on keypress
- Optional number row, corner-hint number row on the top letter row
- Spacebar swipe to switch language
- Copy/cut/paste via long-press on C/X/V
- Offline clipboard history panel
- One-handed and resizing options (saved in settings, on-keyboard control coming soon)"""

        const val ABOUT_TEXT = """BlockVeil Keyboard

A fully offline Android keyboard. No INTERNET permission is requested, so the app cannot send anything anywhere. No keystrokes are logged. Word suggestions come only from a small local word list bundled with the app.

Some features from other keyboard apps are intentionally left out because they require an internet connection (GIF search, personalized suggestions that learn from typed data, cloud voice typing, downloadable language packs) which would break the offline-only design of this app."""

        const val PRIVACY_POLICY = """Privacy Policy for BlockVeil Keyboard

Last updated: 2026

1. Overview
BlockVeil Keyboard ("the App") is an Android input method (keyboard) built with privacy as its core design principle. This policy explains what data the App handles and, just as importantly, what it does not.

2. No Internet Access
The App does not request the INTERNET permission. This is a technical guarantee, not just a promise: without this permission, Android does not allow the App to open any network connection, send any request, or transmit any data off the device, under any circumstance.

3. What We Do Not Collect
We do not collect, store, transmit, or have any access to:
- Anything you type, including passwords, messages, or any other text
- Your contacts, call logs, location, device identifiers, or advertising ID
- Analytics, crash reports, or usage statistics
- Any personally identifiable information

4. What Is Stored, and Where
The following data is stored only in the App's private storage on your device, using Android's SharedPreferences, and is never transmitted anywhere:
- Your keyboard settings and preferences (e.g. auto-capitalization, theme, layout options)
- A short history of text you copy to the clipboard while using the App's clipboard panel (kept locally so you can paste it again; you can clear this at any time from Settings > Clipboard)

This data is removed if you clear the App's storage or uninstall it.

5. Permissions Used
- VIBRATE: used only if you turn on "Vibrate on keypress" in Settings. Used for haptic feedback only.
No other runtime permissions are requested.

6. Third-Party Services
The App uses no third-party analytics, advertising, or crash-reporting SDKs. It makes no calls to any third-party server.

7. Children's Privacy
Because the App collects no personal data of any kind, it does not knowingly collect information from children or anyone else.

8. Changes to This Policy
If this policy changes, the updated version will be included in a future release of the App with a new "Last updated" date above.

9. Contact
This is an independently developed, offline-first keyboard app. For questions about this policy, contact the developer through the app's distribution channel (e.g. the GitHub repository or app listing you obtained it from)."""
    }
}
