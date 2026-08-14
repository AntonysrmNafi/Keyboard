package com.privacykeyboard.app

import android.content.Context

// All keyboard preferences live here as simple boolean flags backed by
// SharedPreferences (on-device only, never leaves the phone).
object SettingsStore {

    private const val PREFS_NAME = "blockveil_settings"

    const val KEY_AUTO_CAPITALIZATION = "auto_capitalization"
    const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
    const val KEY_VIBRATE_ON_KEYPRESS = "vibrate_on_keypress"
    const val KEY_SOUND_ON_KEYPRESS = "sound_on_keypress"
    const val KEY_POPUP_ON_KEYPRESS = "popup_on_keypress"
    const val KEY_SHOW_SUGGESTIONS = "show_suggestions"
    const val KEY_BLOCK_OFFENSIVE_WORDS = "block_offensive_words"
    const val KEY_ENABLE_NUMBER_ROW = "enable_number_row"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean(key, default)

    fun setBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }
}
