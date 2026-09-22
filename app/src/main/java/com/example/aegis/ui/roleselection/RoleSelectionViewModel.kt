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
    object Success : RoleSelectionState()
    data class Error(val message: String) : RoleSelectionState()
}

class RoleSelectionViewModel : ViewModel() {

    private val _state = MutableLiveData<RoleSelectionState>(RoleSelectionState.Idle)
    val state: LiveData<RoleSelectionState> = _state

    // role must be "volunteer" or "organization" - the backend rejects anything else.
    fun selectRole(role: String) {
        _state.value = RoleSelectionState.Saving
        viewModelScope.launch {
            when (val result = safeApiCall("RoleSelectionVM") {
                ServiceLocator.api().setRole(RoleRequest(role))
            }) {
                is ApiResult.Success -> _state.value = RoleSelectionState.Success
                is ApiResult.Failure -> _state.value = RoleSelectionState.Error(result.error.message)
            }
        }
    }
}
