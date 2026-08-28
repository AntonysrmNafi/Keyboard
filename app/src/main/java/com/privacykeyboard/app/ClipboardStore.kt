package com.privacykeyboard.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Keeps a small history of recently copied text and images/screenshots,
// entirely on-device. Nothing leaves the phone; data stored as base64 in
// SharedPreferences. When user copies text, or takes a screenshot (if app
// captures it), it's added here. Can be cleared anytime from Settings > Clipboard.
// Point 8: supports both text and binary (image) data.
object ClipboardStore {

    private const val PREFS_NAME = "blockveil_clipboard"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 10

    data class ClipboardItem(
        val id: String,
        val type: String, // "text" or "image"
        val text: String?, // for text items
        val imageBase64: String?, // for image items (base64-encoded PNG/JPEG)
        val timestamp: Long
    )

    fun getItems(context: Context): List<ClipboardItem> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ClipboardItem(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    text = obj.optString("text").takeIf { it.isNotEmpty() },
                    imageBase64 = obj.optString("imageBase64").takeIf { it.isNotEmpty() },
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addTextItem(context: Context, text: String) {
        if (text.isBlank()) return
        val item = ClipboardItem(
            id = System.currentTimeMillis().toString(),
            type = "text",
            text = text,
            imageBase64 = null,
            timestamp = System.currentTimeMillis()
        )
        addItem(context, item)
    }

    fun addImageItem(context: Context, imageBase64: String) {
        if (imageBase64.isBlank()) return
        val item = ClipboardItem(
            id = System.currentTimeMillis().toString(),
            type = "image",
            text = null,
            imageBase64 = imageBase64,
            timestamp = System.currentTimeMillis()
        )
        addItem(context, item)
    }

    fun removeItem(context: Context, itemId: String) {
        val current = getItems(context).toMutableList()
        current.removeAll { it.id == itemId }
        save(context, current)
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun addItem(context: Context, item: ClipboardItem) {
        val current = getItems(context).toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        while (current.size > MAX_ITEMS) {
            current.removeAt(current.size - 1)
        }
        save(context, current)
    }

    private fun save(context: Context, items: List<ClipboardItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                if (item.text != null) put("text", item.text)
                if (item.imageBase64 != null) put("imageBase64", item.imageBase64)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
