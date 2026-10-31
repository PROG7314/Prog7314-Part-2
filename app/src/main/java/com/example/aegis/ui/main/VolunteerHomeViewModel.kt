package com.example.aegis.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiError
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.repository.ListingRepository
import com.example.aegis.domain.repository.SignupRepository
import com.example.aegis.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// What every screen in ui/main/ exposes: exactly one of these at a time.
sealed interface UiState<out T> {
    object Loading : UiState<Nothing>
    object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val error: ApiError) : UiState<Nothing>
}

/**
 * Reference patter for the Volunteer Home screen. Commitments, Hours, Org Home and Org Listing
 * follow the same shape: a StateFlow<UiState> for the screen, plain functions for user actions,
 * and a SharedFlow for one-off messages (toasts/snackbars).
 *
 * The fragment (extending AegisBaseFragment) collects `feed` and `messages` in
 * viewLifecycleOwner.lifecycleScope with repeatOnLifecycle(STARTED).
 */
class VolunteerHomeViewModel(
    private val listings: ListingRepository,
    private val signups: SignupRepository,
    private val users: UserRepository
) : ViewModel() {

    private val _feed = MutableStateFlow<UiState<List<Listing>>>(UiState.Loading)
    val feed: StateFlow<UiState<List<Listing>>> = _feed.asStateFlow()

    private val _welcomeName = MutableStateFlow("")
    // For "Welcome back, <name>".
    val welcomeName: StateFlow<String> = _welcomeName.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        loadFeed()
        viewModelScope.launch {
            ServiceLocator.connectivity.online.drop(1).filter { it }.collect { loadFeed() }
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _feed.value = UiState.Loading

            // Interests/skills live on the profile (PUT /users/me). The API does the filtering.
            // If the profile call fails we still show an unfiltered feed rather than an error.
            val profile = (users.getMe() as? ApiResult.Success)?.data
            _welcomeName.value = profile?.name.orEmpty()

            val result = listings.getListings(
                causes = profile?.interests.orEmpty(),
                skills = profile?.skills.orEmpty()
            )
            _feed.value = when (result) {
                is ApiResult.Success ->
                    if (result.data.isEmpty()) UiState.Empty else UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            Log.d(TAG, "Feed state: ${_feed.value::class.simpleName}")
        }
    }

    // "I'd like to help" button.
    fun signUp(listingId: String) {
        viewModelScope.launch {
            when (val result = signups.signUp(listingId)) {
                is ApiResult.Success -> {
                    _messages.emit(
                        if (result.data.pendingSync) "You're offline. We'll sign you up as soon as you reconnect."
                        else "You're signed up!"
                    )
                    loadFeed()
                }
                // A full Scheduled listing arrives as conflict; its message is already user-safe.
                is ApiResult.Failure -> _messages.emit(result.error.message)
            }
        }
    }

    companion object {
        private const val TAG = "VolunteerHomeVM"

        // Use with: by viewModels { VolunteerHomeViewModel.Factory }
        val Factory = viewModelFactory {
            initializer {
                VolunteerHomeViewModel(
                    ServiceLocator.listingRepository,
                    ServiceLocator.signupRepository,
                    ServiceLocator.userRepository
                )
            }
        }
    }
}