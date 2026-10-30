//package com.example.aegis.ui.auth
//
//import com.example.aegis.ui.auth.AuthState
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.auth.GoogleAuthProvider
//import com.google.firebase.firestore.FirebaseFirestore
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
//
//class AuthViewModel : ViewModel() {
//
//    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
//    private val firestore = FirebaseFirestore.getInstance()
//
//    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
//    val authState: LiveData<AuthState> = _authState
//
//    fun firebaseAuthWithGoogle(idToken: String) {
//        _authState.value = AuthState.Loading
//        val credential = GoogleAuthProvider.getCredential(idToken, null)
//        auth.signInWithCredential(credential)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    checkOrCreateUserDocument()
//                } else {
//                    _authState.value = AuthState.Error(
//                        mapErrorMessage(task.exception)
//                    )
//                }
//            }
//    }
//
//
//    fun checkExistingSession() {
//        val currentUser = auth.currentUser
//        if (currentUser != null) {
//            _authState.value = AuthState.Loading
//            checkOrCreateUserDocument()
//        } else {
//            _authState.value = AuthState.NotAuthenticated
//        }
//    }
//
//    private fun checkOrCreateUserDocument() {
//        val user = auth.currentUser ?: run {
//            _authState.value = AuthState.Error("No authenticated user found")
//            return
//        }
//
//        viewModelScope.launch {
//            try {
//                val docRef = firestore.collection("users").document(user.uid)
//                val snapshot = docRef.get().await()
//
//                if (!snapshot.exists()) {
//                    // create the base document
//                    docRef.set(
//                        mapOf(
//                            "uid" to user.uid,
//                            "email" to user.email,
//                            "displayName" to user.displayName,
//                            "role" to null,
//                            "onboardingComplete" to false
//                        )
//                    ).await()
//                    _authState.value = AuthState.Success(isNewUser = true, hasRole = false)
//                } else {
//
//                    val role = snapshot.getString("role")
//                    _authState.value = AuthState.Success(
//                        isNewUser = false,
//                        hasRole = !role.isNullOrEmpty()
//                    )
//                }
//            } catch (e: Exception) {
//                _authState.value = AuthState.Error(e.message ?: "Could not verify account")
//            }
//        }
//    }
//
//    private fun mapErrorMessage(exception: Exception?): String {
//        return when {
//            exception == null -> "Sign-in was cancelled"
//            exception.message?.contains("network", ignoreCase = true) == true ->
//                "No internet connection — please check your network and try again"
//            else -> "Sign-in failed — please try again"
//        }
//    }
//}

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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.aegis.R
import com.example.aegis.core.Strings

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

    private suspend fun exchangeForBackendSession(firebaseUser: FirebaseUser) {
        val firebaseIdToken = firebaseUser.getIdToken(false).await()?.token
            ?: error("Could not get Firebase ID token")

        when (val result = safeApiCall("AuthViewModel") {
            ServiceLocator.api().login(LoginRequest(firebaseIdToken))
        }) {
            is ApiResult.Success -> {
                TokenStore.save(getApplication(), result.data.token)
                _authState.value = AuthState.Success(result.data.user.role, result.data.user.isNewUser)
            }
            is ApiResult.Failure -> _authState.value = AuthState.Error(result.error.message)
        }
    }

    private fun mapErrorMessage(e: Exception): String = when {
        e.message?.contains("network", ignoreCase = true) == true ->
            Strings.get(R.string.error_no_internet)
        else -> Strings.get(R.string.sign_in_failed)
    }
}