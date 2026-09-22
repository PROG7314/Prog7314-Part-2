package com.example.aegis.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey


/**
 * Stores the backend-issued JWT in EncryptedSharedPreferences
 * AuthViewModel writes to this after login;
 * ServiceLocator's TokenProvider reads from it on every API call.
 */
object TokenStore {
    private const val FILE_NAME = "aegis_auth"
    private const val KEY_JWT = "sessionToken"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, jwt: String) = prefs(context).edit().putString(KEY_JWT, jwt).apply()
    fun get(context: Context): String? = prefs(context).getString(KEY_JWT, null)
    fun clear(context: Context) = prefs(context).edit().remove(KEY_JWT).apply()
}