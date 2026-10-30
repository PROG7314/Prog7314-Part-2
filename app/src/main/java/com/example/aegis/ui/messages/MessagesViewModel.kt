package com.example.aegis.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Conversation
import com.example.aegis.domain.repository.MessageRepository
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The inbox: every conversation the logged-in user is part of, most recent first. */
class MessagesViewModel(private val messages: MessageRepository) : ViewModel() {

    private val _conversations = MutableStateFlow<UiState<List<Conversation>>>(UiState.Loading)
    val conversations: StateFlow<UiState<List<Conversation>>> = _conversations.asStateFlow()

    fun load() {
        viewModelScope.launch {
            // Reloading after coming back from a chat shouldn't flash the spinner over the list.
            if (_conversations.value !is UiState.Success) _conversations.value = UiState.Loading
            _conversations.value = when (val result = messages.getConversations()) {
                is ApiResult.Success ->
                    if (result.data.isEmpty()) UiState.Empty else UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MessagesViewModel(ServiceLocator.messageRepository) }
        }
    }
}
