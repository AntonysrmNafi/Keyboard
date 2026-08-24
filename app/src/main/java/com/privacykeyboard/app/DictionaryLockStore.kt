package com.privacykeyboard.app

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom

// A single "Dictionary Security Lock" PIN protects the whole My Dictionary section -
// both English and বাংলা together, not one lock per language.
//
// Security notes:
// - Everything here is written through SecureStorage, i.e. AES-256-GCM encrypted at
//   rest with an Android Keystore-held key (Point 1.3), inside the app's private
//   "app data" storage - never Cache, never anywhere another app or a plain file
//   browser could read it.
// - Only a salted SHA-256 hash of the PIN is ever stored. The PIN itself never
//   touches storage and cannot be recovered from the stored hash.
// - The salt is a fresh random value generated on every setPin() call, so the same
//   PIN never produces the same stored hash twice.
// - Changing or removing the lock always requires successfully verifying the current
//   PIN first (enforced by the calling screens); this file exposes no shortcut
//   around that.
// - The only recovery path is the optional recovery email (Point 1.2): the app has
//   no internet access, so no email is ever actually sent - it is a locally stored
//   secret the user chooses, checked as a plain match, and it is subject to its own
//   attempt lockout below, same as the PIN itself.
// - Repeated wrong PINs, and separately repeated wrong recovery emails, each trigger
//   an increasing cooldown, blocking brute-force guessing of either one.
object DictionaryLockStore {

    private const val PREFS_NAME = "blockveil_dictionary_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    private const val KEY_RECOVERY_EMAIL = "recovery_email"
    private const val KEY_RECOVERY_FAIL_COUNT = "recovery_fail_count"
    private const val KEY_RECOVERY_LOCKOUT_UNTIL = "recovery_lockout_until"

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

    // Only ever call this after verifyPin() has already succeeded for the current PIN
    // (or via the email-recovery flow, which itself required a verified email match).
    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_FAIL_COUNT)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    fun cooldownSecondsRemaining(context: Context): Long =
        secondsRemaining(prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L))

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!isLocked(context)) return true
        if (cooldownSecondsRemaining(context) > 0) return false

        val storedHash = prefs(context).getString(KEY_HASH, null) ?: return true
        val salt = prefs(context).getString(KEY_SALT, "") ?: ""
        val matches = storedHash == hash(pin, salt)
        applyAttemptResult(context, matches, KEY_FAIL_COUNT, KEY_LOCKOUT_UNTIL)
        return matches
    }

    // --- Recovery email (Point 1.2) ----------------------------------------------------

    fun hasRecoveryEmail(context: Context): Boolean = prefs(context).contains(KEY_RECOVERY_EMAIL)

    fun getRecoveryEmail(context: Context): String? = prefs(context).getString(KEY_RECOVERY_EMAIL, null)

    fun setRecoveryEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_RECOVERY_EMAIL, normalizeEmail(email)).apply()
    }

    fun clearRecoveryEmail(context: Context) {
        prefs(context).edit().remove(KEY_RECOVERY_EMAIL).apply()
    }

    fun recoveryCooldownSecondsRemaining(context: Context): Long =
        secondsRemaining(prefs(context).getLong(KEY_RECOVERY_LOCKOUT_UNTIL, 0L))

    fun verifyRecoveryEmail(context: Context, email: String): Boolean {
        if (recoveryCooldownSecondsRemaining(context) > 0) return false
        val stored = getRecoveryEmail(context) ?: return false
        val matches = stored == normalizeEmail(email)
        applyAttemptResult(context, matches, KEY_RECOVERY_FAIL_COUNT, KEY_RECOVERY_LOCKOUT_UNTIL)
        return matches
    }

    // --- shared helpers -----------------------------------------------------------------

    private fun applyAttemptResult(context: Context, matches: Boolean, failKey: String, lockoutKey: String) {
        val editor = prefs(context).edit()
        if (matches) {
            editor.putInt(failKey, 0).putLong(lockoutKey, 0L)
        } else {
            val failCount = prefs(context).getInt(failKey, 0) + 1
            editor.putInt(failKey, failCount)
            if (failCount >= 5) {
                // 5th miss -> 30s cooldown, then doubling: 30s, 60s, 120s, up to 16 minutes.
                val cooldownSeconds = 30L * (1L shl minOf(failCount - 5, 5))
                editor.putLong(lockoutKey, SystemClock.elapsedRealtime() + cooldownSeconds * 1000)
            }
        }
        editor.apply()
    }

    private fun secondsRemaining(untilElapsedRealtime: Long): Long {
        val remainingMs = untilElapsedRealtime - SystemClock.elapsedRealtime()
        return if (remainingMs > 0) (remainingMs / 1000) + 1 else 0
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

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

    private fun prefs(context: Context) = SecureStorage.prefs(context, PREFS_NAME)
}
