package com.example.aegis.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Applies the user's theme and language choices to the running app. Safe to call repeatedly. */
object Appearance {

    /** null = the user hasn't chosen yet, so follow the phone's own light/dark setting. */
    fun applyDarkMode(enabled: Boolean?) {
        AppCompatDelegate.setDefaultNightMode(
            when (enabled) {
                null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                true -> AppCompatDelegate.MODE_NIGHT_YES
                false -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    fun applyLanguage(code: String) {
        val wanted = LocaleListCompat.forLanguageTags(code)
        // Setting the same locales again would needlessly recreate the activity.
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }
}
