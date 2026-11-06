package com.example.aegis.ui.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.R
import com.example.aegis.core.Strings
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.storage.CloudinaryImageUploader
import com.example.aegis.data.storage.ImageUploader
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.UserProfile
import com.example.aegis.domain.repository.UserRepository
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads the signed-in user's profile and saves edits to it (PUT /users/me), uploading a new photo first. */
class EditProfileViewModel(
    private val users: UserRepository,
    private val uploader: ImageUploader
) : ViewModel() {

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

    // The photo the user picked but has not saved yet. Held here so it survives a rotation.
    private val _pickedPhoto = MutableStateFlow<Uri?>(null)
    val pickedPhoto: StateFlow<Uri?> = _pickedPhoto.asStateFlow()

    // If the upload worked but the profile save failed, retrying must not upload the same photo twice.
    private var uploaded: Pair<Uri, String>? = null

    init {
        viewModelScope.launch {
            _profile.value = when (val result = users.getMe()) {
                is ApiResult.Success -> UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    fun setPickedPhoto(uri: Uri) {
        _pickedPhoto.value = uri
    }

    /** Sends only the fields this role is allowed to change; the API returns 400 for the others. */
    fun save(isOrg: Boolean, name: String, about: String, interests: List<String>, skills: List<String>) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            val imageUrl = try {
                uploadPickedPhoto()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Photo upload failed: ${e.message}")
                _saveState.value = SaveState.Error(Strings.get(R.string.profile_photo_upload_failed))
                return@launch
            }

            val result = if (isOrg) {
                users.updateProfile(name = name.trim(), description = about.trim(), profileImageUrl = imageUrl)
            } else {
                users.updateProfile(
                    name = name.trim(), bio = about.trim(), profileImageUrl = imageUrl,
                    interests = interests, skills = skills
                )
            }
            _saveState.value = when (result) {
                is ApiResult.Success -> {
                    Log.i(TAG, "Profile saved for role=${if (isOrg) "organization" else "volunteer"}, photo=${imageUrl != null}")
                    SaveState.Saved
                }
                is ApiResult.Failure -> {
                    Log.w(TAG, "Profile save failed: ${result.error.message}")
                    SaveState.Error(result.error.message)
                }
            }
        }
    }

    /** The https URL of the picked photo, or null when nothing new was picked. */
    private suspend fun uploadPickedPhoto(): String? {
        val photo = _pickedPhoto.value ?: return null
        uploaded?.takeIf { it.first == photo }?.let { return it.second }
        val url = uploader.upload(photo)
        uploaded = photo to url
        return url
    }

    /** Called once the screen has shown a result, so it isn't shown again after a rotation. */
    fun consumeSaveState() {
        _saveState.value = SaveState.Idle
    }

    companion object {
        private const val TAG = "EditProfile"
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                EditProfileViewModel(ServiceLocator.userRepository, CloudinaryImageUploader(app))
            }
        }
    }
}