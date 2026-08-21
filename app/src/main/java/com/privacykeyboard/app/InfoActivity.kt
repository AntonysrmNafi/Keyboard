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

        const val ABOUT_TEXT = """BlockVeil Keyboard is a privacy-first Android keyboard built for people who simply want a fast, reliable typing experience without being watched.

Every keystroke is processed entirely on your device. Word suggestions are drawn from a compact dictionary bundled with the app, your personal dictionary and clipboard history are stored only in the app's private storage, and nothing about what you type is ever collected, profiled, or shared with anyone, including us.

BlockVeil supports English, Bangla phonetic and প্রভাত (Bangla traditional) typing, an offline suggestion bar, a personal dictionary you can optionally lock with a PIN, and a range of layout and appearance options, all wrapped in a clean, distraction-free design.

Certain features common in other keyboards (GIF search, cloud-synced suggestions, downloadable language packs) are intentionally left out, since they would require sending data off the device, which goes against what BlockVeil is built to be."""

        const val PRIVACY_POLICY = """Privacy Policy for BlockVeil Keyboard

Last updated: 2026

1. Overview
BlockVeil Keyboard ("the App", "we", "our") is an Android input method built with privacy as its core design principle. This policy explains, in full, what data the App handles, where it is stored, and what it does not do.

2. No Internet Access
The App does not request the INTERNET permission. This is a technical guarantee, not just a promise: without this permission, Android does not allow the App to open any network connection, send any request, or transmit any data off the device, under any circumstance.

3. What We Do Not Collect
We do not collect, store, transmit, or have any access to:
- Anything you type, including passwords, messages, or any other text
- Your contacts, call logs, location, device identifiers, or advertising ID
- Analytics, crash reports, or usage statistics
- Any personally identifiable information

4. What Is Stored On Your Device, and Where
The following data is stored only in the App's private storage, using Android's SharedPreferences, and is never transmitted anywhere:
- Your keyboard settings and preferences (auto-capitalization, layout options, appearance, etc.)
- A short local history of text you copy to the clipboard, kept only so you can paste it again from the App's clipboard panel (clear anytime from Settings > Clipboard)
- Words you add to your personal dictionary, kept separately per language and never mixed with the App's bundled word list
- If you choose to set a Dictionary Lock PIN, only a one-way cryptographic hash of that PIN is stored; the PIN itself is never stored in plain text and cannot be recovered from the hash

All of this data is removed if you clear the App's storage or uninstall it.

5. Permissions Used
- VIBRATE: used only if you turn on "Vibrate on keypress" in Settings, for haptic feedback only.
No other runtime permissions are requested.

6. Third-Party Services
The App uses no third-party analytics, advertising, or crash-reporting SDKs, and makes no calls to any third-party server.

7. Children's Privacy
Because the App collects no personal data of any kind, it does not knowingly collect information from children or anyone else.

8. Changes to This Policy
If this policy changes, the updated version will be included in a future release of the App with a new "Last updated" date above.

9. Contact
This is an independently developed, offline-first keyboard app. For questions about this policy, contact the developer through the App's distribution channel (e.g. the GitHub repository or app listing you obtained it from)."""

        const val TERMS_AND_CONDITIONS = """Terms & Conditions for BlockVeil Keyboard

Last updated: 2026

1. Acceptance of Terms
By installing, enabling, or using BlockVeil Keyboard ("the App"), you agree to these Terms & Conditions. If you do not agree, please do not use the App.

2. License
You are granted a personal, non-exclusive, non-transferable, revocable license to install and use the App on devices you own or control, for personal, non-commercial use, unless separately agreed otherwise.

3. Nature of the App
The App is an offline Android input method (keyboard). It requests no INTERNET permission and cannot send data off your device. Its only requested runtime permission is VIBRATE, used solely for optional haptic feedback on keypress.

4. Your Responsibility
You are solely responsible for the content you type using the App, including in any third-party app you use it with. The App does not review, moderate, or have any visibility into what you type.

5. Local Data and Backups
Settings, clipboard history, and your personal dictionary are stored only in the App's private storage on your device. We are not responsible for data loss resulting from uninstalling the App, clearing app storage, or device loss or damage. Back up anything important through your own means.

6. No Warranty
The App is provided "as is" and "as available", without warranties of any kind, whether express or implied, including but not limited to fitness for a particular purpose, accuracy of suggestions, or uninterrupted, error-free operation.

7. Limitation of Liability
To the maximum extent permitted by law, the developer shall not be liable for any indirect, incidental, or consequential damages arising from your use of, or inability to use, the App.

8. Third-Party Distribution
If you obtained the App's source code or an unsigned build (for example via GitHub Actions), you are responsible for verifying the build and for how you distribute or install it.

9. Changes to the App or These Terms
Features, screens, and these Terms may change in future releases. Continued use of the App after an update constitutes acceptance of the then-current Terms.

10. Governing Law
These Terms are governed by applicable local law in the jurisdiction where the developer operates, without regard to conflict-of-law principles.

11. Contact
Questions about these Terms can be directed to the developer through the App's distribution channel (e.g. the GitHub repository or app listing you obtained it from)."""

        const val FAQ_TEXT = """Frequently Asked Questions

Is BlockVeil Keyboard really offline?
Yes. The App does not request the INTERNET permission, so Android does not allow it to open any network connection at all, not even in the background.

Does it collect or send anything I type?
No. Nothing you type is logged, collected, or transmitted anywhere. See Privacy Policy for full details.

How do I enable and switch to BlockVeil Keyboard?
From the home screen, tap "Enable Keyboard" to open your system keyboard settings and turn it on, then tap "Switch to BlockVeil Keyboard" (or use your device's keyboard switch icon) while typing in any text field.

How do I change typing modes?
Tap the "Aa/অ" key on the spacebar row, or swipe left/right on the spacebar, to cycle between English and প্রভাত (Bangla traditional).

Why is Bangla Phonetic mode unavailable right now?
It is temporarily out of the mode-switching cycle while it is refined further. It has not been removed and will return in a future update.

How do word suggestions work?
Suggestions come from a small offline dictionary bundled with the App, based on what you have typed so far. You can expand this list yourself in Settings > Dictionary.

What is My Dictionary, and how is it different from the BlockVeil Dictionary?
BlockVeil Dictionary is the App's bundled, read-only word list. My Dictionary is your own personal word list, entirely separate from it. You can add, edit, and delete your own words from Settings > Dictionary > My Dictionary.

What is Dictionary Lock?
An optional PIN you can set per language to keep your personal dictionary private. Once set, opening that language's My Dictionary asks for the PIN first. Only a secure hash of the PIN is ever stored, never the PIN itself.

Where is my clipboard history stored, and can I clear it?
Locally on your device only. You can view and clear it anytime from Settings > Clipboard.

How do I build or update the App myself?
The project builds through GitHub Actions. Push the repository to GitHub, run the "Build APK" workflow from the Actions tab, then download and install the generated APK."""
    }
}
