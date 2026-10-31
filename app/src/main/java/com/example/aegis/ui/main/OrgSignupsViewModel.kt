package com.example.aegis.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.repository.SignupRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Organisation view of ONE listing's sign-ups: load them, mark one complete (or edit its hours).
 */
class OrgSignupsViewModel(
    private val signupRepository: SignupRepository
) : ViewModel() {

    private val _signups = MutableStateFlow<UiState<List<Signup>>>(UiState.Loading)
    val signups: StateFlow<UiState<List<Signup>>> = _signups.asStateFlow()

    // One-off error messages (already safe to show) for a Toast.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var listingId: String? = null

    /** Safe to call on every onViewCreated: it only loads when the listing changes. */
    fun load(id: String) {
        if (listingId == id) return
        listingId = id
        fetch(showSpinner = true)
    }

    fun retry() = fetch(showSpinner = true)

    private fun fetch(showSpinner: Boolean) {
        val id = listingId ?: return
        viewModelScope.launch {
            if (showSpinner) _signups.value = UiState.Loading
            _signups.value = when (val result = signupRepository.getListingSignups(id)) {
                is ApiResult.Success ->
                    if (result.data.isEmpty()) UiState.Empty else UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            Log.d(TAG, "Sign-ups state: ${_signups.value::class.simpleName}")
        }
    }

    /** Marks the sign-up complete with [hours]; calling it again on a completed one edits the hours. */
    fun complete(signup: Signup, hours: Double) {
        viewModelScope.launch {
            when (val result = signupRepository.completeSignup(signup.id, hours)) {
                is ApiResult.Success -> fetch(showSpinner = false) // refresh in place, no flicker
                is ApiResult.Failure -> _messages.tryEmit(result.error.message)
            }
        }
    }

    companion object {
        private const val TAG = "OrgSignupsVM"

        val Factory = viewModelFactory {
            initializer {
                OrgSignupsViewModel(ServiceLocator.signupRepository)
            }
        }
    }
}