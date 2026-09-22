package com.example.aegis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.local.UserPreferencesDataStore
import kotlinx.coroutines.launch

class SettingsViewModel(private val prefsStore: UserPreferencesDataStore) : ViewModel() {

    val darkMode = prefsStore.darkModeFlow
    val notificationsEnabled = prefsStore.notificationsFlow
    val language = prefsStore.languageFlow

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setDarkMode(enabled) }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch { prefsStore.setNotificationsEnabled(enabled) }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { prefsStore.setLanguage(code) }
    }
}