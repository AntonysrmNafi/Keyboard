package com.privacykeyboard.app

import android.content.Context
import org.json.JSONArray

// Backing store for the user's personal dictionary, kept separately per language.
// Entirely on-device (SharedPreferences), nothing ever leaves the phone.
// Add/Edit/View UI lands in a future update; Clear All already works against
// whatever is here.
object UserDictionaryStore {

    private const val PREFS_NAME = "blockveil_user_dictionary"

    private fun key(isBangla: Boolean) = if (isBangla) "words_bn" else "words_en"

    fun getWords(context: Context, isBangla: Boolean): List<String> {
        val raw = prefs(context).getString(key(isBangla), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context, isBangla: Boolean) {
        prefs(context).edit().remove(key(isBangla)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
