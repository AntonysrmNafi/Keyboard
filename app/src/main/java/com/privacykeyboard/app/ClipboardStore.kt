package com.privacykeyboard.app

import android.content.Context
import org.json.JSONArray

// Keeps a small history of recently copied text, entirely on-device.
// Nothing here ever leaves the phone; it is just a JSON array in SharedPreferences.
object ClipboardStore {

    private const val PREFS_NAME = "blockveil_clipboard"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 10

    fun getItems(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addItem(context: Context, text: String) {
        if (text.isBlank()) return
        val current = getItems(context).toMutableList()
        current.remove(text)
        current.add(0, text)
        while (current.size > MAX_ITEMS) {
            current.removeAt(current.size - 1)
        }
        save(context, current)
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
