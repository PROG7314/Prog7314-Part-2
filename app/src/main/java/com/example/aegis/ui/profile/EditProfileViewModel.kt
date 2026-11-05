package com.example.aegis.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.UserProfile
import com.example.aegis.domain.repository.UserRepository
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads the signed-in user's profile and saves edits to it (PUT /users/me). */
class EditProfileViewModel(private val users: UserRepository) : ViewModel() {

    sealed interface SaveState {
        object Idle : SaveState
        object Saving : SaveState
        object Saved : SaveState
        data class Error(val message: String) : SaveState
    }

    private val _profile = MutableStateFlow<UiState<UserProfile>>(UiState.Loading)
    val profile: StateFlow<UiState<UserProfile>> = _profile.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    init {
        viewModelScope.launch {
            _profile.value = when (val result = users.getMe()) {
                is ApiResult.Success -> UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    /** Sends only the fields this role is allowed to change; the API returns 400 for the others. */
    fun save(isOrg: Boolean, name: String, about: String, interests: List<String>, skills: List<String>) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            val result = if (isOrg) {
                users.updateProfile(name = name.trim(), description = about.trim())
            } else {
                users.updateProfile(name = name.trim(), bio = about.trim(), interests = interests, skills = skills)
            }
            _saveState.value = when (result) {
                is ApiResult.Success -> {
                    Log.i(TAG, "Profile saved for role=${if (isOrg) "organization" else "volunteer"}")
                    SaveState.Saved
                }
                is ApiResult.Failure -> {
                    Log.w(TAG, "Profile save failed: ${result.error.message}")
                    SaveState.Error(result.error.message)
                }
            }
        }
    }

    /** Called once the screen has shown a result, so it isn't shown again after a rotation. */
    fun consumeSaveState() {
        _saveState.value = SaveState.Idle
    }

    companion object {
        private const val TAG = "EditProfile"
        val Factory = viewModelFactory {
            initializer { EditProfileViewModel(ServiceLocator.userRepository) }
        }
    }
}