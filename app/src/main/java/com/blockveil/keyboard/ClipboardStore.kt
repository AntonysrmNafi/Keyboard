package com.blockveil.keyboard

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
    // Point: default expiry for clipboard history entries - an item older
    // than this is dropped the next time the list is read or written to.
    private const val EXPIRY_MS = 60 * 60 * 1000L // 1 hour
    // Point: onPrimaryClipChanged can fire more than once for a single
    // system copy (a known Android platform quirk, not something this app
    // triggers itself) - if the same text arrives again within this window,
    // treat it as the same copy event and just refresh its timestamp
    // instead of inserting a visible duplicate row.
    private const val DEDUPE_WINDOW_MS = 3000L

    data class ClipboardItem(
        val id: String,
        val type: String, // "text" or "image"
        val text: String?, // for text items
        val imageBase64: String?, // for image items (base64-encoded PNG/JPEG)
        val timestamp: Long,
        val pinned: Boolean = false
    )

    fun getItems(context: Context): List<ClipboardItem> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        val all = try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ClipboardItem(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    text = obj.optString("text").takeIf { it.isNotEmpty() },
                    imageBase64 = obj.optString("imageBase64").takeIf { it.isNotEmpty() },
                    timestamp = obj.getLong("timestamp"),
                    pinned = obj.optBoolean("pinned", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        val now = System.currentTimeMillis()
        // Point: a pinned item is exempt from the 1-hour expiry - "stay
        // there permanently" means pinning overrides auto-delete.
        val fresh = all.filter { it.pinned || now - it.timestamp < EXPIRY_MS }
        if (fresh.size != all.size) {
            // Point: persist the expiry too, not just hide expired items -
            // otherwise they'd reappear if something re-reads the raw prefs.
            save(context, fresh)
        }
        return fresh
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

    // Point: shared by every path that turns a picked/copied/screenshotted
    // image into a base64 string for storage (ClipboardSettingsActivity's
    // New Clip image picker, BlockVeilInputMethodService's explicit-copy
    // listener, and ScreenshotJobService's auto-capture) - downscales to
    // maxDim on the longest side and compresses as JPEG, since clipboard
    // history is for quick reuse, not full-resolution storage, and this all
    // lives in SharedPreferences which shouldn't balloon after a few images.
    fun encodeImageUriToBase64(context: Context, uri: android.net.Uri, maxDim: Int = 1024): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null
            val scale = (maxDim.toFloat() / maxOf(original.width, original.height)).coerceAtMost(1f)
            val scaled = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
                )
            } else {
                original
            }
            val outputStream = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    fun removeItem(context: Context, itemId: String) {
        val current = getItems(context).toMutableList()
        current.removeAll { it.id == itemId }
        save(context, current)
    }

    // Point: toggles pinned on/off for one item (second click un-pins).
    fun togglePin(context: Context, itemId: String) {
        val current = getItems(context).toMutableList()
        val idx = current.indexOfFirst { it.id == itemId }
        if (idx == -1) return
        current[idx] = current[idx].copy(pinned = !current[idx].pinned)
        save(context, current)
    }

    // Point: overwrites the stored text of one item in place (keeps its id,
    // timestamp and pinned state) - used by Settings > Clipboard's "Edit
    // Clip" sheet Save button.
    fun updateText(context: Context, itemId: String, newText: String) {
        val current = getItems(context).toMutableList()
        val idx = current.indexOfFirst { it.id == itemId }
        if (idx == -1) return
        current[idx] = current[idx].copy(text = newText)
        save(context, current)
    }

    // Point: rewrites storage in exactly this id order - used by Settings >
    // Clipboard's drag-to-reorder. Any stored item NOT mentioned in
    // orderedIds (shouldn't normally happen) is kept, appended at the end,
    // so a reorder call can never silently drop data.
    fun reorder(context: Context, orderedIds: List<String>) {
        val current = getItems(context)
        val byId = current.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        val remaining = current.filter { it.id !in orderedIds }
        save(context, reordered + remaining)
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun addItem(context: Context, item: ClipboardItem) {
        val current = getItems(context).toMutableList()
        current.removeAll { it.id == item.id }
        // Point: dedupe against a very recent identical copy (same type +
        // text) instead of always inserting a new row - see DEDUPE_WINDOW_MS
        // above for why this is needed.
        val dupeIdx = current.indexOfFirst {
            it.type == item.type && it.text == item.text &&
                item.timestamp - it.timestamp < DEDUPE_WINDOW_MS
        }
        if (dupeIdx != -1) {
            current[dupeIdx] = current[dupeIdx].copy(timestamp = item.timestamp)
        } else {
            current.add(0, item)
        }
        // Point: only trim unpinned items when over the cap - a pinned item
        // should stay ("permanently") even past MAX_ITEMS worth of new
        // copies coming in.
        while (current.count { !it.pinned } > MAX_ITEMS) {
            val oldestUnpinnedIdx = current.indexOfLast { !it.pinned }
            if (oldestUnpinnedIdx == -1) break
            current.removeAt(oldestUnpinnedIdx)
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
                put("pinned", item.pinned)
            }
            array.put(obj)
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Point: shared preview-truncation helper for anywhere clipboard items
    // are listed (the keyboard's own clipboard panel, and Settings >
    // Clipboard) - caps at PREVIEW_MAX_CHARS total, forced onto rows of at
    // most PREVIEW_CHARS_PER_LINE characters each (ignoring any newlines
    // already in the original text, so a long single-line copy and a
    // multi-line copy preview the same way). Never touches the stored
    // ClipboardItem.text itself, and pasting always uses the full text -
    // this is display-only.
    const val PREVIEW_MAX_CHARS = 120
    const val PREVIEW_CHARS_PER_LINE = 25
    const val PREVIEW_MAX_LINES = 5 // ceil(120 / 25)

    fun buildPreview(text: String): String {
        val capped = if (text.length > PREVIEW_MAX_CHARS) text.substring(0, PREVIEW_MAX_CHARS) else text
        val sb = StringBuilder()
        var i = 0
        while (i < capped.length) {
            val end = (i + PREVIEW_CHARS_PER_LINE).coerceAtMost(capped.length)
            sb.append(capped, i, end)
            if (end < capped.length) sb.append('\n')
            i = end
        }
        return sb.toString()
    }
}
