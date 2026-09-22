package com.example.aegis.ui.roleselection

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.aegis.R

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
                viewModel.selectRole("organization")
            }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RoleSelectionState.Success -> {
                    if (state.role == "organization") {
                        findNavController().navigate(R.id.action_roleSelectionFragment_to_orgHomeFragment)
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