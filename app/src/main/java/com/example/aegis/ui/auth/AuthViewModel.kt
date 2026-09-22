package com.example.aegis.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun firebaseAuthWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    checkOrCreateUserDocument()
                } else {
                    _authState.value = AuthState.Error(
                        mapErrorMessage(task.exception)
                    )
                }
            }
    }


    fun checkExistingSession() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _authState.value = AuthState.Loading
            checkOrCreateUserDocument()
        } else {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    private fun checkOrCreateUserDocument() {
        val user = auth.currentUser ?: run {
            _authState.value = AuthState.Error("No authenticated user found")
            return
        }

        viewModelScope.launch {
            try {
                val docRef = firestore.collection("users").document(user.uid)
                val snapshot = docRef.get().await()

                if (!snapshot.exists()) {
                    // create the base document
                    docRef.set(
                        mapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "displayName" to user.displayName,
                            "role" to null,
                            "onboardingComplete" to false
                        )
                    ).await()
                    _authState.value = AuthState.Success(isNewUser = true, hasRole = false)
                } else {

                    val role = snapshot.getString("role")
                    _authState.value = AuthState.Success(
                        isNewUser = false,
                        hasRole = !role.isNullOrEmpty()
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Could not verify account")
            }
        }
    }

    private fun mapErrorMessage(exception: Exception?): String {
        return when {
            exception == null -> "Sign-in was cancelled"
            exception.message?.contains("network", ignoreCase = true) == true ->
                "No internet connection — please check your network and try again"
            else -> "Sign-in failed — please try again"
        }
    }
}