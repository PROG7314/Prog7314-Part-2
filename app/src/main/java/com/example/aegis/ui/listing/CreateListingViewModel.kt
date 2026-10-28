package com.example.aegis.ui.listing

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.storage.CloudinaryImageUploader
import com.example.aegis.data.storage.ImageUploader
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.repository.ListingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * The create-listing screen. Holds what must survive a screen rotation (picked date/time, skills,
 * photos), checks the form, uploads the photos, then creates the listing.
 */
class CreateListingViewModel(
    private val listings: ListingRepository,
    private val uploader: ImageUploader
) : ViewModel() {

    data class FormError(val field: FormField, val message: String)

    // Picked in dialogs, so the Fragment's text boxes can't hold them across a rotation.
    var date: LocalDate? = null
    var time: LocalTime? = null
    val skills = mutableSetOf<String>()

    private val _images = MutableStateFlow<List<Uri>>(emptyList())
    val images: StateFlow<List<Uri>> = _images.asStateFlow()

    /** True from tapping Save until the listing is created (or fails). */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _formErrors = MutableSharedFlow<FormError>(extraBufferCapacity = 1)
    val formErrors: SharedFlow<FormError> = _formErrors.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _created = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val created: SharedFlow<Unit> = _created.asSharedFlow()

    // Photos already uploaded. If creating the listing fails, a retry doesn't upload them again.
    private val uploaded = mutableMapOf<Uri, String>()

    fun setImages(uris: List<Uri>) {
        _images.value = uris.distinct().take(MAX_IMAGES)
    }

    fun save(input: ListingFormInput) {
        if (_saving.value) return // double tap
        when (val result = ListingFormValidator.validate(input)) {
            is FormResult.Invalid -> _formErrors.tryEmit(FormError(result.field, result.message))
            is FormResult.Valid -> submit(result.draft)
        }
    }

    private fun submit(draft: ListingDraft) {
        _saving.value = true
        viewModelScope.launch {
            try {
                val urls = uploadImages() ?: return@launch // it already told the user what went wrong
                when (val result = listings.createListing(draft.copy(images = urls))) {
                    is ApiResult.Success -> {
                        reset()
                        _created.emit(Unit)
                    }
                    is ApiResult.Failure -> _messages.emit(result.error.message)
                }
            } finally {
                _saving.value = false
            }
        }
    }

    /** Returns the https URLs in the order picked, or null if any photo failed. */
    private suspend fun uploadImages(): List<String>? {
        val urls = mutableListOf<String>()
        for (uri in _images.value) {
            val done = uploaded[uri]
            if (done != null) {
                urls += done
                continue
            }
            try {
                val url = uploader.upload(uri)
                uploaded[uri] = url
                urls += url
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Photo upload failed", e) // full reason + stack trace in Logcat
                // Only a real network problem says "connection"; anything else shows what went wrong.
                val noNetwork = e is java.net.UnknownHostException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.ConnectException
                _messages.emit(
                    if (noNetwork) "Couldn't upload your photos. Check your connection and try again."
                    else "Photo upload failed: ${e.message?.take(160)}"
                )
                return null
            }
        }
        return urls
    }

    /** Back to an empty form (after a successful save, or Discard). */
    fun reset() {
        date = null
        time = null
        skills.clear()
        uploaded.clear()
        _images.value = emptyList()
    }

    companion object {
        const val MAX_IMAGES = 3
        private const val TAG = "CreateListingVM"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CreateListingViewModel(ServiceLocator.listingRepository, CloudinaryImageUploader(app))
            }
        }
    }
}