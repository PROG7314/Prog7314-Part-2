package com.example.aegis.ui.roleselection

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.aegis.R
import com.google.android.material.snackbar.Snackbar

class RoleSelectionFragment : Fragment(R.layout.fragment_role_selection) {

    private val viewModel: RoleSelectionViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.volunteerCard)
            .setOnClickListener {
                viewModel.selectRole("volunteer")
            }

        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.organisationCard)
            .setOnClickListener {
                viewModel.selectRole("organisation")
            }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RoleSelectionState.Success -> {
                    if (state.role == "organisation") {
                        Snackbar.make(view, "Organisation account set up — dashboard coming soon", Snackbar.LENGTH_LONG).show()
                    } else {
                        findNavController().navigate(R.id.action_roleSelectionFragment_to_interestMatchingFragment)
                    }
                }
                is RoleSelectionState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}