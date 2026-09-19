package com.example.aegis.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.aegis.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginFragment : Fragment(R.layout.fragment_login) {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val authViewModel: AuthViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            authViewModel.firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.e("AegisAuth", "Google sign-in failed: ${e.statusCode}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        view.findViewById<Button>(R.id.signInButton).setOnClickListener {
            googleSignInClient.signInIntent.let { intent ->
                signInLauncher.launch(intent)
            }
        }

        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Success -> {
                    Log.d("AegisAuth", "Sign-in successful")
                    // Navigate to your Role Selection destination —
                    // replace this ID with whatever Member 1 actually named it in nav_graph.xml
                    findNavController().navigate(R.id.action_loginFragment_to_SettingsFragment)
                }
                is AuthState.Error -> Log.e("AegisAuth", state.message)
                else -> {}
            }
        }
    }
}