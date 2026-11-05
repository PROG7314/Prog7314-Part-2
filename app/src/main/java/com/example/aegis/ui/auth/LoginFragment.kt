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
import com.example.aegis.data.local.UserPreferencesDataStore
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.first
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
                Toast.makeText(requireContext(), R.string.login_enter_both, Toast.LENGTH_SHORT).show()
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
                emailSignInButton.setText(R.string.create_account)
                toggleModeText.setText(R.string.have_account_log_in)
            } else {
                emailSignInButton.setText(R.string.log_in)
                toggleModeText.setText(R.string.new_here_create_account)
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
                    offerBiometricThenNavigate(state.role)
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

    private fun navigateByRole(role: String?) {
        when (role) {
            null -> findNavController().navigate(R.id.action_loginFragment_to_roleSelectionFragment)
            "volunteer" -> findNavController().navigate(R.id.action_loginFragment_to_mainFragment)
            "organization" -> findNavController().navigate(R.id.action_loginFragment_to_orgHomeFragment)
        }
    }

    /** Asks once, after the first sign-in, whether to turn on fingerprint unlock, then continues. */
    private fun offerBiometricThenNavigate(role: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = UserPreferencesDataStore(requireContext())
            val alreadyHandled = prefs.biometricEnabledFlow.first() || prefs.biometricAskedFlow.first()
            if (alreadyHandled || !BiometricHelper.isAvailable(requireContext())) {
                navigateByRole(role)
                return@launch
            }
            prefs.setBiometricAsked(true)
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.biometric_enable_title)
                .setMessage(R.string.biometric_enable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.biometric_yes) { _, _ ->
                    BiometricHelper.prompt(
                        requireActivity(),
                        getString(R.string.biometric_title),
                        getString(R.string.biometric_subtitle),
                        getString(android.R.string.cancel),
                        onSuccess = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                prefs.setBiometricEnabled(true)
                                navigateByRole(role)
                            }
                        },
                        onFailure = { navigateByRole(role) }
                    )
                }
                .setNegativeButton(R.string.biometric_not_now) { _, _ -> navigateByRole(role) }
                .show()
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
            authViewModel.reportError(getString(R.string.sign_in_failed))
        }
    }

    private fun mapCredentialError(e: GetCredentialException): String = when (e) {
        is GetCredentialCancellationException -> getString(R.string.sign_in_cancelled)
        is NoCredentialException -> getString(R.string.no_google_account)
        else -> getString(R.string.sign_in_failed)
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.email_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_password_title)
            .setMessage(R.string.reset_password_message)
            .setView(input)
            .setPositiveButton(R.string.reset_send) { _, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.reset_enter_email, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = authViewModel.sendPasswordReset(email)
                    val message = if (result.isSuccess) {
                        getString(R.string.reset_link_sent)
                    } else {
                        result.exceptionOrNull()?.message ?: getString(R.string.reset_failed)
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}