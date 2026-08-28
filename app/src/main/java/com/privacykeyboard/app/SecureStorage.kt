package com.privacykeyboard.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Every sensitive value this app stores on disk - Dictionary Security Lock PIN hash
// and salt, the optional recovery email, and every word in My Dictionary - is written
// through this one encrypted store instead of plain SharedPreferences.
//
// The file itself still lives where SharedPreferences always live: the app's private
// internal storage (Settings > Apps > BlockVeil Keyboard > Storage > "App data"),
// never Cache, never external/shared storage. What changes is that its *contents* are
// AES-256-GCM encrypted with a key that is generated inside, and never leaves, the
// device's hardware-backed Android Keystore. Even if the raw file were ever pulled off
// the device (a rooted phone, a forensic tool, a leaked backup), it would only be
// unreadable ciphertext without that key, and the key cannot be exported or copied
// to another device.
object SecureStorage {

    fun prefs(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context.applicationContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
