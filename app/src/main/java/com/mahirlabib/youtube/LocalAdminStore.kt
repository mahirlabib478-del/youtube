package com.mahirlabib.youtube

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Device-local admin gate. This hides admin tools from the normal UI; it is not
 * equivalent to server-side authorization on a rooted or modified device.
 */
object LocalAdminStore {
    private const val PREFS = "youtube_local_admin"
    private const val SALT_KEY = "pin_salt"
    private const val HASH_KEY = "pin_hash"

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getString(SALT_KEY, null).isNullOrBlank() &&
            !prefs.getString(HASH_KEY, null).isNullOrBlank()
    }

    fun setPin(context: Context, pin: String, confirmation: String) {
        require(pin.matches(Regex("^\\d{6,12}$"))) { "PIN must contain 6–12 digits." }
        require(pin == confirmation) { "PINs do not match." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, hash(salt, pin))
            .commit()) { "Could not save the admin PIN on this device." }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("^\\d{6,12}$"))) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saltText = prefs.getString(SALT_KEY, null) ?: return false
        val expected = prefs.getString(HASH_KEY, null) ?: return false
        val salt = runCatching { Base64.decode(saltText, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = hash(salt, pin)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(salt + pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
