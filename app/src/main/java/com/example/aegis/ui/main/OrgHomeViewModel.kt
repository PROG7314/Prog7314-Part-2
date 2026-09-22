package com.example.aegis.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aegis.data.ServiceLocator
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.repository.ListingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrgHomeViewModel(
    private val listingRepository: ListingRepository
) : ViewModel() {

    private val _listings = MutableStateFlow<UiState<List<Listing>>>(UiState.Loading)
    val listings: StateFlow<UiState<List<Listing>>> = _listings.asStateFlow()

    init {
        loadListings()
    }

    fun loadListings() {
        viewModelScope.launch {
            _listings.value = UiState.Loading
            val result = listingRepository.getListings()
            _listings.value = when (result) {
                is ApiResult.Success ->
                    if (result.data.isEmpty()) UiState.Empty else UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            Log.d(TAG, "Listings state: ${_listings.value::class.simpleName}")
        }
    }

    companion object {
        private const val TAG = "OrgHomeVM"

        val Factory = viewModelFactory {
            initializer {
                OrgHomeViewModel(ServiceLocator.listingRepository)
            }
        }
    }
}