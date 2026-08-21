package com.privacykeyboard.app

import android.content.Context
import java.security.MessageDigest

// PIN lock for "My Dictionary", set independently per language (English / বাংলা).
// The PIN itself is never stored in plain text, only its SHA-256 hash, and it never
// leaves the phone (SharedPreferences only), same as every other store in this app.
object DictionaryLockStore {

    private const val PREFS_NAME = "blockveil_dictionary_lock"

    private fun key(isBangla: Boolean) = if (isBangla) "pin_hash_bn" else "pin_hash_en"

    fun isLocked(context: Context, isBangla: Boolean): Boolean =
        prefs(context).contains(key(isBangla))

    fun setPin(context: Context, isBangla: Boolean, pin: String) {
        prefs(context).edit().putString(key(isBangla), hash(pin)).apply()
    }

    fun clearPin(context: Context, isBangla: Boolean) {
        prefs(context).edit().remove(key(isBangla)).apply()
    }

    fun verifyPin(context: Context, isBangla: Boolean, pin: String): Boolean {
        val stored = prefs(context).getString(key(isBangla), null) ?: return true
        return stored == hash(pin)
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
