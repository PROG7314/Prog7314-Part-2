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
import com.example.aegis.domain.model.Listing
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
import java.time.ZoneId

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

    // ---- Edit mode (null editingId = creating a new listing) ----
    var editingId: String? = null
        private set
    private var original: Listing? = null

    /** The Fragment fills the form from this once, then sets this to true so a rotation doesn't redo it. */
    var prefillApplied = false

    private val _prefill = MutableStateFlow<Listing?>(null)
    val prefill: StateFlow<Listing?> = _prefill.asStateFlow()

    /** The listing's saved photos. Kept unless the user picks new ones (which replace them). */
    private val _existingImages = MutableStateFlow<List<String>>(emptyList())
    val existingImages: StateFlow<List<String>> = _existingImages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loadFailed: SharedFlow<Unit> = _loadFailed.asSharedFlow()

    /** Loads the listing to edit. Safe to call on every onViewCreated. */
    fun startEdit(id: String) {
        if (editingId == id) return
        editingId = id
        _loading.value = true
        viewModelScope.launch {
            when (val result = listings.getListing(id)) {
                is ApiResult.Success -> {
                    val l = result.data
                    original = l
                    date = l.dateTime?.atZone(ZoneId.systemDefault())?.toLocalDate()
                    time = l.dateTime?.atZone(ZoneId.systemDefault())?.toLocalTime()?.withSecond(0)?.withNano(0)
                    skills.clear()
                    skills.addAll(l.skillsRequired)
                    _existingImages.value = l.images
                    _prefill.value = l
                }
                is ApiResult.Failure -> {
                    editingId = null
                    _messages.emit(result.error.message)
                    _loadFailed.emit(Unit)
                }
            }
            _loading.value = false
        }
    }

    // Photos already uploaded. If creating the listing fails, a retry doesn't upload them again.
    private val uploaded = mutableMapOf<Uri, String>()

    fun setImages(uris: List<Uri>) {
        _images.value = uris.distinct().take(MAX_IMAGES)
    }

    fun save(input: ListingFormInput) {
        if (_saving.value || _loading.value) return // double tap, or the listing hasn't loaded yet
        when (val result = ListingFormValidator.validate(input, originalDateTime = original?.dateTime)) {
            is FormResult.Invalid -> _formErrors.tryEmit(FormError(result.field, result.message))
            is FormResult.Valid -> submit(result.draft)
        }
    }

    private fun submit(draft: ListingDraft) {
        _saving.value = true
        viewModelScope.launch {
            try {
                // No new photos picked while editing: keep the saved ones. Otherwise upload the new ones.
                val urls = if (_images.value.isEmpty()) _existingImages.value
                else (uploadImages() ?: return@launch) // it already told the user what went wrong
                val finished = draft.copy(images = urls)
                val saved = editingId?.let { listings.updateListing(it, finished) }
                    ?: listings.createListing(finished)
                when (val result = saved) {
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