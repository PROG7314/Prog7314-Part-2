package com.example.aegis.ui.auth

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()

    object NotAuthenticated : AuthState()
    data class Success(val isNewUser: Boolean, val hasRole: Boolean) : AuthState()
    data class Error(val message: String) : AuthState()
}