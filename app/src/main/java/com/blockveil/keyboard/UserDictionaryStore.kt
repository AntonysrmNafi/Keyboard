package com.blockveil.keyboard

import android.content.Context
import org.json.JSONArray

// Backing store for the user's personal dictionary, kept separately per language and
// completely independent from the bundled BlockVeil dictionary (DictionaryProvider) -
// words added here never mix with, and are never sourced from, the bundled word lists.
// Every word (present and future) is written through SecureStorage, i.e. AES-256-GCM
// encrypted at rest with an Android Keystore-held key, inside the app's private
// "app data" storage - never Cache, and nothing ever leaves the phone.
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

    fun addWord(context: Context, isBangla: Boolean, word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val current = getWords(context, isBangla).toMutableList()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        current.add(trimmed)
        save(context, isBangla, current)
    }

    fun updateWord(context: Context, isBangla: Boolean, oldWord: String, newWord: String) {
        val trimmed = newWord.trim()
        if (trimmed.isEmpty()) return
        val current = getWords(context, isBangla).toMutableList()
        val index = current.indexOf(oldWord)
        if (index == -1) return
        current[index] = trimmed
        save(context, isBangla, current)
    }

    fun removeWord(context: Context, isBangla: Boolean, word: String) {
        val current = getWords(context, isBangla).toMutableList()
        current.remove(word)
        save(context, isBangla, current)
    }

    fun clear(context: Context, isBangla: Boolean) {
        prefs(context).edit().remove(key(isBangla)).apply()
    }

    private fun save(context: Context, isBangla: Boolean, words: List<String>) {
        val array = JSONArray()
        words.forEach { array.put(it) }
        prefs(context).edit().putString(key(isBangla), array.toString()).apply()
    }

    private fun prefs(context: Context) = SecureStorage.prefs(context, PREFS_NAME)
}
