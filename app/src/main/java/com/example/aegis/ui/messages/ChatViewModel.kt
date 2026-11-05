package com.example.aegis.ui.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.ChatMessage
import com.example.aegis.domain.repository.MessageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One open conversation. The screen calls refresh() on a timer while it is visible; each call asks
 * the API only for messages newer than the last one shown.
 */
class ChatViewModel(
    private val conversationId: String,
    val otherPartyId: String,
    private val messages: MessageRepository
) : ViewModel() {

    data class ChatState(
        val loading: Boolean = true,
        val messages: List<ChatMessage> = emptyList(),
        val errorMessage: String? = null,
        val sending: Boolean = false
    )

    sealed interface Event {
        object Sent : Event
        data class SendFailed(val message: String) : Event
    }

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var refreshing = false

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            val since = _state.value.messages.lastOrNull()?.sentAt
            when (val result = messages.getMessages(conversationId, since)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, errorMessage = null, messages = ChatMerge.merge(it.messages, result.data)) }
                    // Anything the other person wrote is now on screen, so it counts as read.
                    if (result.data.any { it.senderId == otherPartyId }) markRead()
                }
                is ApiResult.Failure -> {
                    Log.w(TAG, "Refresh failed: ${result.error.message}")
                    // A failed timer tick is silent; only a failed first load shows an error.
                    _state.update { if (it.loading) it.copy(loading = false, errorMessage = result.error.message) else it }
                }
            }
            refreshing = false
        }
    }

    /** Retry after the first load failed. */
    fun retry() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        refresh()
    }

    fun send(raw: String?) {
        val text = MessageText.clean(raw) ?: return
        if (_state.value.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            when (val result = messages.sendMessage(conversationId, text)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(sending = false, messages = ChatMerge.merge(it.messages, listOf(result.data))) }
                    _events.emit(Event.Sent)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(sending = false) }
                    _events.emit(Event.SendFailed(result.error.message))
                }
            }
        }
    }

    private fun markRead() {
        viewModelScope.launch { messages.markRead(conversationId) }
    }

    private companion object {
        const val TAG = "ChatVM"
    }
}
