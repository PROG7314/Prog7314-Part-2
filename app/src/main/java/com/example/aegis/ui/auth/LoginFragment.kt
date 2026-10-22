package com.example.aegis.ui.auth

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.aegis.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val authViewModel: AuthViewModel by viewModels()
    private var isSignUpMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailInput = view.findViewById<EditText>(R.id.emailInput)
        val passwordInput = view.findViewById<EditText>(R.id.passwordInput)
        val forgotPasswordText = view.findViewById<TextView>(R.id.forgotPasswordText)
        val emailSignInButton = view.findViewById<Button>(R.id.emailSignInButton)
        val toggleModeText = view.findViewById<TextView>(R.id.toggleModeText)
        val googleSignInButton = view.findViewById<Button>(R.id.signInButton)

        googleSignInButton.setOnClickListener { launchGoogleSignIn() }

        emailSignInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isSignUpMode) {
                authViewModel.signUpWithEmail(email, password)
            } else {
                authViewModel.signInWithEmail(email, password)
            }
        }

        toggleModeText.setOnClickListener {
            isSignUpMode = !isSignUpMode
            if (isSignUpMode) {
                emailSignInButton.text = "Create account"
                toggleModeText.text = "Already have an account? Log in"
            } else {
                emailSignInButton.text = "Log in"
                toggleModeText.text = "New here? Create an account"
            }
        }

        forgotPasswordText.setOnClickListener { showForgotPasswordDialog() }

        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Loading -> {
                    emailSignInButton.isEnabled = false
                    googleSignInButton.isEnabled = false
                }
                is AuthState.Success -> {
                    emailSignInButton.isEnabled = true
                    googleSignInButton.isEnabled = true
                    when (state.role) {
                        null -> findNavController().navigate(R.id.action_loginFragment_to_roleSelectionFragment)
                        "volunteer" -> findNavController().navigate(R.id.action_loginFragment_to_mainFragment)
                        "organization" -> findNavController().navigate(R.id.action_loginFragment_to_orgHomeFragment)
                    }
                }
                is AuthState.Error -> {
                    emailSignInButton.isEnabled = true
                    googleSignInButton.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun launchGoogleSignIn() {
        val credentialManager = CredentialManager.create(requireContext())

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(requireContext(), request)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                Log.e("AegisAuth", "Credential Manager sign-in failed: ${e::class.simpleName}")
                authViewModel.reportError(mapCredentialError(e))
            }
        }
    }

    private fun handleGoogleCredential(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            authViewModel.firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            authViewModel.reportError("Sign-in failed — please try again")
        }
    }

    private fun mapCredentialError(e: GetCredentialException): String = when (e) {
        is GetCredentialCancellationException -> "Sign-in was cancelled"
        is NoCredentialException -> "No Google account found on this device. Try email sign-in instead."
        else -> "Sign-in failed — please try again"
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Reset password")
            .setMessage("Enter your account email and we'll send a reset link.")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter an email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = authViewModel.sendPasswordReset(email)
                    val message = if (result.isSuccess) {
                        "Reset link sent — check your inbox"
                    } else {
                        result.exceptionOrNull()?.message ?: "Could not send reset email"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}