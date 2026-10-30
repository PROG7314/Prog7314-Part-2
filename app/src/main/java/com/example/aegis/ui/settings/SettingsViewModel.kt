package com.example.aegis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.core.Appearance
import com.example.aegis.data.local.UserPreferencesDataStore
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Every change is applied to the app straight away, saved locally so it survives a restart,
 * and sent to PUT /users/me/settings. If the API call fails the local value still stands.
 */
class SettingsViewModel(
    private val prefsStore: UserPreferencesDataStore,
    private val userRepository: UserRepository,
    // Until the user flips the switch the app follows the phone, so the switch should show that.
    systemIsDark: Boolean
) : ViewModel() {

    val darkMode = prefsStore.chosenDarkModeFlow.map { it ?: systemIsDark }
    val notificationsEnabled = prefsStore.notificationsFlow
    val language = prefsStore.languageFlow

    private val _syncFailed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val syncFailed: SharedFlow<String> = _syncFailed

    fun toggleDarkMode(enabled: Boolean) {
        Appearance.applyDarkMode(enabled)
        viewModelScope.launch {
            prefsStore.setDarkMode(enabled)
            report(userRepository.updateSettings(darkMode = enabled))
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setNotificationsEnabled(enabled)
            report(userRepository.updateSettings(notificationsEnabled = enabled))
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            prefsStore.setLanguage(code)
            report(userRepository.updateSettings(preferredLanguage = code))
            // Last: this recreates the activity, so the save and API call are already underway.
            Appearance.applyLanguage(code)
        }
    }

    private fun report(result: ApiResult<Unit>) {
        if (result is ApiResult.Failure) _syncFailed.tryEmit(result.error.message)
    }
}
