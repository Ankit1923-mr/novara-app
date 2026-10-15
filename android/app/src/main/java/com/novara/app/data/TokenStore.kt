package com.novara.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for the current session's JWT and the email it belongs
 * to. Backed by EncryptedSharedPreferences (AndroidX Security), unlike
 * ProfileStore's plain SharedPreferences - a bearer token must never be
 * stored as an ordinary plaintext preference.
 */
class TokenStore(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private const val PREFS_NAME = "novara_secure_session"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EMAIL = "authenticated_email"
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_TOKEN, value).apply() }

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) { prefs.edit().putString(KEY_EMAIL, value).apply() }

    val isLoggedIn: Boolean get() = token != null && email != null

    fun clear() {
        prefs.edit().clear().apply()
    }
}
