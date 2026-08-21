package com.privacykeyboard.app

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom

// A single "Dictionary Security Lock" PIN protects the whole My Dictionary section -
// both English and বাংলা together, not one lock per language.
//
// Security notes:
// - Only a salted SHA-256 hash of the PIN is ever written to disk. The PIN itself
//   never touches storage and cannot be recovered from the stored hash.
// - The salt is a fresh random value generated on every setPin() call, so the same
//   PIN never produces the same stored hash twice.
// - There is deliberately no "forgot PIN" / reset path anywhere in this class or in
//   the UI that calls it. Changing or removing the lock always requires successfully
//   verifying the current PIN first (enforced by the caller); this file exposes no
//   shortcut around that.
// - Repeated wrong PINs trigger an increasing cooldown (verifyPin() refuses to even
//   check the PIN while a cooldown is active), which blocks brute-force guessing.
object DictionaryLockStore {

    private const val PREFS_NAME = "blockveil_dictionary_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"

    fun isLocked(context: Context): Boolean = prefs(context).contains(KEY_HASH)

    fun setPin(context: Context, pin: String) {
        val salt = randomSalt()
        prefs(context).edit()
            .putString(KEY_HASH, hash(pin, salt))
            .putString(KEY_SALT, salt)
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    // Only ever call this after verifyPin() has already succeeded for the current PIN.
    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_FAIL_COUNT)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    // Seconds still left before another PIN attempt is allowed, or 0 if not in cooldown.
    fun cooldownSecondsRemaining(context: Context): Long {
        val until = prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L)
        val remainingMs = until - SystemClock.elapsedRealtime()
        return if (remainingMs > 0) (remainingMs / 1000) + 1 else 0
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!isLocked(context)) return true
        if (cooldownSecondsRemaining(context) > 0) return false

        val storedHash = prefs(context).getString(KEY_HASH, null) ?: return true
        val salt = prefs(context).getString(KEY_SALT, "") ?: ""
        val matches = storedHash == hash(pin, salt)
        val editor = prefs(context).edit()

        if (matches) {
            editor.putInt(KEY_FAIL_COUNT, 0).putLong(KEY_LOCKOUT_UNTIL, 0L)
        } else {
            val failCount = prefs(context).getInt(KEY_FAIL_COUNT, 0) + 1
            editor.putInt(KEY_FAIL_COUNT, failCount)
            if (failCount >= 5) {
                // 5th miss -> 30s cooldown, then doubling: 30s, 60s, 120s, up to 16 minutes.
                val cooldownSeconds = 30L * (1L shl minOf(failCount - 5, 5))
                editor.putLong(KEY_LOCKOUT_UNTIL, SystemClock.elapsedRealtime() + cooldownSeconds * 1000)
            }
        }
        editor.apply()
        return matches
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
