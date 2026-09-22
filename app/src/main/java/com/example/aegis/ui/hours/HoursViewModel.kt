package com.example.aegis.ui.hours

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.HoursSummary
import com.example.aegis.domain.repository.SignupRepository
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Volunteer's Hours screen. GET /users/me/hours-log. */
class HoursViewModel(
    private val signups: SignupRepository
) : ViewModel() {

    private val _hours = MutableStateFlow<UiState<HoursSummary>>(UiState.Loading)
    val hours: StateFlow<UiState<HoursSummary>> = _hours.asStateFlow()

    init {
        loadHours()
    }

    fun loadHours() {
        viewModelScope.launch {
            _hours.value = UiState.Loading
            val result = signups.getHoursSummary()
            _hours.value = when (result) {
                is ApiResult.Success ->
                    if (result.data.entries.isEmpty()) UiState.Empty else UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            Log.d(TAG, "Hours state: ${_hours.value::class.simpleName}")
        }
    }

    companion object {
        private const val TAG = "HoursVM"

        val Factory = viewModelFactory {
            initializer {
                HoursViewModel(ServiceLocator.signupRepository)
            }
        }
    }
}