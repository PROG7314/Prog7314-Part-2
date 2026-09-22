package com.example.aegis.ui.roleselection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.remote.dto.RoleRequest
import com.example.aegis.data.remote.safeApiCall
import com.example.aegis.domain.model.ApiResult
import kotlinx.coroutines.launch

sealed class RoleSelectionState {
    object Idle : RoleSelectionState()
    object Saving : RoleSelectionState()
    data class Success(val role: String) : RoleSelectionState()
    data class Error(val message: String) : RoleSelectionState()
}

class RoleSelectionViewModel : ViewModel() {

    private val _state = MutableLiveData<RoleSelectionState>(RoleSelectionState.Idle)
    val state: LiveData<RoleSelectionState> = _state

    // role must be "volunteer" or "organization" - the backend rejects anything else.
    fun selectRole(role: String) {
        _state.value = RoleSelectionState.Saving
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .set(mapOf(
                        "uid" to uid,
                        "email" to auth.currentUser?.email,
                        "displayName" to auth.currentUser?.displayName,
                        "role" to role,
                        "onboardingComplete" to false
                    ), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                _state.value = RoleSelectionState.Success(role)
            } catch (e: Exception) {
                _state.value = RoleSelectionState.Error(e.message ?: "Failed to save role")
            }
        }
    }
}
