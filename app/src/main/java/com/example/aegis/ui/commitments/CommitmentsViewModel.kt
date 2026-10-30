package com.example.aegis.ui.commitments

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Conversation
import com.example.aegis.domain.repository.MessageRepository
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.SignupStatus
import com.example.aegis.domain.repository.SignupRepository
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Volunteer's Commitments screen. GET /users/me/signups returns every status; only CONFIRMED
 * ones are shown here (a completed one no longer needs "Dismiss", a cancelled one is already gone).
 */
class CommitmentsViewModel(
    private val signups: SignupRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _commitments = MutableStateFlow<UiState<List<Signup>>>(UiState.Loading)
    val commitments: StateFlow<UiState<List<Signup>>> = _commitments.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Fired when "Message" has opened (or found) the thread with the organization.
    private val _chatEvents = MutableSharedFlow<Conversation>(extraBufferCapacity = 1)
    val chatEvents: SharedFlow<Conversation> = _chatEvents.asSharedFlow()

    init {
        loadCommitments()
    }

    /** "Message" button: open the conversation about this commitment's listing. */
    fun openChat(signup: Signup) {
        viewModelScope.launch {
            when (val result = messageRepository.openConversation(signup.listingId)) {
                is ApiResult.Success -> _chatEvents.emit(result.data)
                is ApiResult.Failure -> _messages.emit(result.error.message)
            }
        }
    }

    fun loadCommitments() {
        viewModelScope.launch {
            _commitments.value = UiState.Loading
            val result = signups.getMySignups()
            _commitments.value = when (result) {
                is ApiResult.Success -> {
                    val confirmed = result.data.filter { it.status == SignupStatus.CONFIRMED }
                    if (confirmed.isEmpty()) UiState.Empty else UiState.Success(confirmed)
                }
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            Log.d(TAG, "Commitments state: ${_commitments.value::class.simpleName}")
        }
    }

    /** "Dismiss" button = cancel the sign-up. */
    fun dismiss(signupId: String) {
        viewModelScope.launch {
            when (val result = signups.cancelSignup(signupId)) {
                is ApiResult.Success -> loadCommitments() // refresh so the card disappears
                is ApiResult.Failure -> _messages.emit(result.error.message)
            }
        }
    }

    companion object {
        private const val TAG = "CommitmentsVM"

        val Factory = viewModelFactory {
            initializer {
                CommitmentsViewModel(ServiceLocator.signupRepository, ServiceLocator.messageRepository)
            }
        }
    }
}