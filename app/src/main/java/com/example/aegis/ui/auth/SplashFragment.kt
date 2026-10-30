package com.example.aegis.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.aegis.R

class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            Log.d("SplashFragment", "authState = $state")
            val nav = findNavController()
            when (state) {
                is AuthState.NotAuthenticated -> nav.navigate(R.id.action_splashFragment_to_loginFragment)
                is AuthState.Success -> when (state.role) {
                    null -> nav.navigate(R.id.action_splashFragment_to_roleSelectionFragment)
                    "volunteer" -> nav.navigate(R.id.action_splashFragment_to_mainFragment)
                    "organization" -> nav.navigate(R.id.action_splashFragment_to_orgHomeFragment)
                    else -> {
                        Log.e("SplashFragment", "Unknown role '${state.role}'")
                        nav.navigate(R.id.action_splashFragment_to_loginFragment)
                    }
                }
                is AuthState.Error -> {
                    Log.e("SplashFragment", "Auth error: ${state.message}")
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    nav.navigate(R.id.action_splashFragment_to_loginFragment)
                }
                else -> {} // Idle / Loading: waiting for the backend
            }
        }

        authViewModel.checkExistingSession()
    }
}