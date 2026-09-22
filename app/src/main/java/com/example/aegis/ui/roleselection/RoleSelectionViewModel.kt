package com.example.aegis.ui.roleselection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class RoleSelectionState {
    object Idle : RoleSelectionState()
    object Saving : RoleSelectionState()
    object Success : RoleSelectionState()
    data class Error(val message: String) : RoleSelectionState()
}

class RoleSelectionViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableLiveData<RoleSelectionState>(RoleSelectionState.Idle)
    val state: LiveData<RoleSelectionState> = _state

    fun selectRole(role: String) {
        val uid = auth.currentUser?.uid ?: run {
            _state.value = RoleSelectionState.Error("No signed-in user found")
            return
        }

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
                _state.value = RoleSelectionState.Success
            } catch (e: Exception) {
                _state.value = RoleSelectionState.Error(e.message ?: "Failed to save role")
            }
        }
    }
}