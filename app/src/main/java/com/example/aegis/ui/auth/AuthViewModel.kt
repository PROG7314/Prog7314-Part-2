package com.example.aegis.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.TokenStore
import com.example.aegis.data.remote.dto.LoginRequest
import com.example.aegis.data.remote.safeApiCall
import com.example.aegis.domain.model.ApiResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.aegis.notifications.NotificationTokenSync

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun firebaseAuthWithGoogle(googleIdToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val firebaseUser = firebaseAuth.signInWithCredential(credential).await().user
                    ?: error("No authenticated user found")
                exchangeForBackendSession(firebaseUser)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapErrorMessage(e))
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val firebaseUser = firebaseAuth.signInWithEmailAndPassword(email, password).await().user
                    ?: error("No authenticated user found")
                exchangeForBackendSession(firebaseUser)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapErrorMessage(e))
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val firebaseUser = firebaseAuth.createUserWithEmailAndPassword(email, password).await().user
                    ?: error("No authenticated user found")
                exchangeForBackendSession(firebaseUser)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapErrorMessage(e))
            }
        }
    }

    /** Fire-and-forget from a dialog; the Fragment shows its own success/failure message. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(mapErrorMessage(e)))
    }

    fun checkExistingSession() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            _authState.value = AuthState.NotAuthenticated
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                exchangeForBackendSession(currentUser)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapErrorMessage(e))
            }
        }
    }

    /** Used by LoginFragment for errors that happen before Firebase is even involved (e.g. Credential Manager cancellation). */
    fun reportError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    private suspend fun exchangeForBackendSession(firebaseUser: FirebaseUser) {
        val firebaseIdToken = firebaseUser.getIdToken(false).await()?.token
            ?: error("Could not get Firebase ID token")

        when (val result = safeApiCall("AuthViewModel") {
            ServiceLocator.api().login(LoginRequest(firebaseIdToken))
        }) {
            is ApiResult.Success -> {
                TokenStore.save(getApplication(), result.data.token)
                // JWT is saved first because the FCM call needs it. Separate launch: best effort, never blocks login.
                viewModelScope.launch { NotificationTokenSync.sync() }
                _authState.value = AuthState.Success(result.data.user.role, result.data.user.isNewUser)
            }
            is ApiResult.Failure -> _authState.value = AuthState.Error(result.error.message)
        }
    }

    private fun mapErrorMessage(e: Exception): String = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please try again."
        is FirebaseAuthInvalidUserException -> "We couldn't find an account with that email."
        is FirebaseAuthUserCollisionException -> "An account already exists with that email."
        is FirebaseAuthWeakPasswordException -> "Please choose a stronger password (at least 6 characters)."
        else -> if (e.message?.contains("network", ignoreCase = true) == true)
            "No internet connection — please check your network and try again"
        else "Sign-in failed — please try again"
    }
}