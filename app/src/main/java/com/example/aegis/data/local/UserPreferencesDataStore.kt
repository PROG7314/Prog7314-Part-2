package com.example.aegis.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferencesDataStore(private val context: Context) {

    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LANGUAGE = stringPreferencesKey("language")
        val SELECTED_CAUSES = stringSetPreferencesKey("selected_causes")
        val SELECTED_SKILLS = stringSetPreferencesKey("selected_skills")

        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")

        val BIOMETRIC_ASKED = booleanPreferencesKey("biometric_asked")
    }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[BIOMETRIC_ENABLED] ?: false }

    val biometricAskedFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[BIOMETRIC_ASKED] ?: false }


    val darkModeFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[DARK_MODE] ?: false }

    val notificationsFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[NOTIFICATIONS_ENABLED] ?: true }

    val languageFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[LANGUAGE] ?: "en" }

    // Null until the user flips the Settings switch, so the app follows the phone's theme until then.
    val chosenDarkModeFlow: Flow<Boolean?> = context.dataStore.data
        .map { prefs -> prefs[DARK_MODE] }

    // Null until the user has picked a language in Settings, so startup can tell "chose English"
    // from "never chose" and leave a system-level per-app language alone.
    val chosenLanguageFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[LANGUAGE] }

    val selectedCausesFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[SELECTED_CAUSES] ?: emptySet() }

    val selectedSkillsFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[SELECTED_SKILLS] ?: emptySet() }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { it[LANGUAGE] = languageCode }
    }
    suspend fun setSelectedCauses(causes: Set<String>) {
        context.dataStore.edit { it[SELECTED_CAUSES] = causes }
    }

    suspend fun setSelectedSkills(skills: Set<String>) {
        context.dataStore.edit { it[SELECTED_SKILLS] = skills }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setBiometricAsked(asked: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ASKED] = asked }
    }
}