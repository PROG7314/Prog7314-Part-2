package com.example.aegis.ui.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.repository.ListingRepository
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** An organisation's view of ONE of its listings: show it, and delete it. (Edit reuses the create form.) */
class OrgListingDetailViewModel(
    private val listings: ListingRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Listing>>(UiState.Loading)
    val state: StateFlow<UiState<Listing>> = _state.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    private var listingId: String? = null

    /**
     * Called from onStart, so the screen also refreshes when you come back from Edit or Sign-ups.
     * The spinner only shows the first time; later reloads happen in place.
     */
    fun load(id: String) {
        listingId = id
        fetch(showSpinner = _state.value !is UiState.Success)
    }

    fun retry() = fetch(showSpinner = true)

    private fun fetch(showSpinner: Boolean) {
        val id = listingId ?: return
        viewModelScope.launch {
            if (showSpinner) _state.value = UiState.Loading
            when (val result = listings.getListing(id)) {
                is ApiResult.Success -> _state.value = UiState.Success(result.data)
                // A failed refresh keeps showing what we already have.
                is ApiResult.Failure ->
                    if (_state.value is UiState.Success) _messages.tryEmit(result.error.message)
                    else _state.value = UiState.Error(result.error)
            }
        }
    }

    fun delete() {
        val id = listingId ?: return
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                when (val result = listings.deleteListing(id)) {
                    is ApiResult.Success -> _deleted.emit(Unit)
                    is ApiResult.Failure -> _messages.emit(result.error.message)
                }
            } finally {
                _deleting.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OrgListingDetailViewModel(ServiceLocator.listingRepository) }
        }
    }
}