package com.example.aegis.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.aegis.R

class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.NotAuthenticated -> {
                    findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
                }
                is AuthState.Success -> {
                    when (state.role) {
                        null -> findNavController().navigate(R.id.action_splashFragment_to_roleSelectionFragment)
                        "volunteer" -> findNavController().navigate(R.id.action_splashFragment_to_mainFragment)
                        "organization" -> findNavController().navigate(R.id.action_splashFragment_to_orgHomeFragment)
                    }
                }
                is AuthState.Error -> {
                    // Fallback — treat as not logged in if something goes wrong
                    findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
                }
                else -> {}
            }
        }

        authViewModel.checkExistingSession()
    }
}