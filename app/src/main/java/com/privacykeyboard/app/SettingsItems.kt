package com.privacykeyboard.app

// One switchable row: which preference key it controls, its label text, and its default.
data class ToggleItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val default: Boolean
)

object SettingsItems {

    const val SECTION_PREFERENCES = "preferences"
    const val SECTION_APPEARANCE = "appearance"
    const val SECTION_TEXT_CORRECTION = "text_correction"

    val preferences = listOf(
        ToggleItem(
            SettingsStore.KEY_AUTO_CAPITALIZATION,
            "Auto-capitalization",
            "Capitalize the first word of each sentence",
            true
        ),
        ToggleItem(
            SettingsStore.KEY_DOUBLE_SPACE_PERIOD,
            "Double-space period",
            "Double tap space inserts a period and a space",
            true
        ),
        ToggleItem(
            SettingsStore.KEY_VIBRATE_ON_KEYPRESS,
            "Vibrate on keypress",
            "Short vibration on every key",
            false
        ),
        ToggleItem(
            SettingsStore.KEY_SOUND_ON_KEYPRESS,
            "Sound on keypress",
            "Play the system click sound on every key",
            false
        ),
        ToggleItem(
            SettingsStore.KEY_POPUP_ON_KEYPRESS,
            "Popup on keypress",
            "Show a preview bubble above the pressed key",
            true
        )
    )

    val appearance = listOf(
        ToggleItem(
            SettingsStore.KEY_ENABLE_NUMBER_ROW,
            "Enable number row",
            "Adds a 0-9 row above the letters (English and Bangla phonetic modes)",
            false
        )
    )

    val textCorrection = listOf(
        ToggleItem(
            SettingsStore.KEY_SHOW_SUGGESTIONS,
            "Show suggestions",
            "Display the offline suggestion bar while typing",
            true
        ),
        ToggleItem(
            SettingsStore.KEY_BLOCK_OFFENSIVE_WORDS,
            "Block offensive words",
            "Do not suggest words from the built-in blocklist",
            true
        )
    )

    fun forSection(section: String): Pair<String, List<ToggleItem>> = when (section) {
        SECTION_APPEARANCE -> "Appearance & Layouts" to appearance
        SECTION_TEXT_CORRECTION -> "Text correction" to textCorrection
        else -> "Preferences" to preferences
    }
}
