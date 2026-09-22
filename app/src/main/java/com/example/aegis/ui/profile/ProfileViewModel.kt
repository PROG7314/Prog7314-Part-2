package com.example.aegis.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.UserProfile
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared Profile screen for both volunteers and organizations - GET /users/me. */
class ProfileViewModel(
    private val users: com.example.aegis.domain.repository.UserRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<UiState<UserProfile>>(UiState.Loading)
    val profile: StateFlow<UiState<UserProfile>> = _profile.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _profile.value = UiState.Loading
            _profile.value = when (val result = users.getMe()) {
                is ApiResult.Success -> UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProfileViewModel(ServiceLocator.userRepository) }
        }
    }
}
