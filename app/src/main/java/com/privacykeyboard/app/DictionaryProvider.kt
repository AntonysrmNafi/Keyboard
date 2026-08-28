package com.privacykeyboard.app

import android.content.Context

// Loads small offline word lists from assets, no network involved.
// Expand bn_words.txt / en_words.txt (one word per line) to grow suggestions.
object DictionaryProvider {

    private var bnWords: List<String> = emptyList()
    private var enWords: List<String> = emptyList()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        bnWords = readAssetLines(context, "bn_words.txt")
        enWords = readAssetLines(context, "en_words.txt")
        loaded = true
    }

    private fun readAssetLines(context: Context, fileName: String): List<String> {
        return try {
            context.assets.open(fileName).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun suggestions(prefix: String, isBangla: Boolean, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val source = if (isBangla) bnWords else enWords
        val query = if (isBangla) prefix else prefix.lowercase()
        return source.filter {
            val candidate = if (isBangla) it else it.lowercase()
            candidate.startsWith(query)
        }.take(limit)
    }

    fun allWords(isBangla: Boolean): List<String> = if (isBangla) bnWords else enWords

    fun wordCount(isBangla: Boolean): Int = allWords(isBangla).size
}
