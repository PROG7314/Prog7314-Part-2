package com.example.aegis.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.TokenStore
import com.example.aegis.data.remote.safeApiCall
import com.example.aegis.databinding.FragmentProfileBinding
import com.example.aegis.domain.model.UserProfile
import com.example.aegis.ui.main.UiState
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.util.Log

class ProfileFragment : AegisBaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.retryButton.setOnClickListener { viewModel.load() }
        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_global_settingsFragment)
        }
        binding.logoutButton.setOnClickListener { logout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profile.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: UiState<UserProfile>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.contentGroup.visibility = View.GONE

        when (state) {
            is UiState.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
            is UiState.Empty -> Unit
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
            }
            is UiState.Success -> bindProfile(state.data)
        }
    }

    private fun bindProfile(profile: UserProfile) {
        binding.contentGroup.visibility = View.VISIBLE

        binding.nameText.text = profile.name
        binding.emailText.text = profile.email.orEmpty()

        val isOrg = profile.role == "organization"
        binding.roleBadge.text = getString(if (isOrg) R.string.role_organization else R.string.role_volunteer)
        binding.aboutLabel.text = getString(if (isOrg) R.string.about_us else R.string.about_me)
        binding.aboutText.text = (if (isOrg) profile.description else profile.bio)
            ?.ifBlank { null } ?: getString(R.string.no_bio_yet)

        if (profile.profileImageUrl.isNullOrBlank()) {
            binding.avatarImage.setImageResource(R.drawable.profile)
        } else {
            binding.avatarImage.load(profile.profileImageUrl) {
                placeholder(R.drawable.profile)
                error(R.drawable.profile)
            }
        }

        binding.causesSection.visibility = if (isOrg) View.GONE else View.VISIBLE
        if (!isOrg) {
            bindReadOnlyChips(binding.causesChipGroup, profile.interests)
            bindReadOnlyChips(binding.skillsChipGroup, profile.skills)
        }
    }

    private fun bindReadOnlyChips(group: ChipGroup, values: List<String>) {
        group.removeAllViews()
        values.forEach { value ->
            val chip = Chip(requireContext())
            chip.text = value
            chip.isClickable = false
            chip.isCheckable = false
            group.addView(chip)
        }
    }

    /**
     * Logout button. First try to send anything made offline (the token is still valid). If changes
     * are still unsent - no connection, or the server refused - ask before throwing them away.
     */
    private fun logout() {
        binding.logoutButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            if (ServiceLocator.connectivity.isOnline) ServiceLocator.syncEngine.syncPending()

            val unsynced = ServiceLocator.database.pendingDao().allUnsynced().size
            if (unsynced == 0) {
                finishLogout()
            } else {
                showUnsyncedWarning(unsynced)
            }
        }
    }
    private fun showUnsyncedWarning(count: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_unsynced_title)
            .setMessage(resources.getQuantityString(R.plurals.logout_unsynced_message, count, count))
            .setNegativeButton(R.string.logout_stay) { _, _ -> _binding?.logoutButton?.isEnabled = true }
            .setPositiveButton(R.string.logout_anyway) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch { finishLogout() }
            }
            .setOnCancelListener { _binding?.logoutButton?.isEnabled = true } // tapped outside / back
            .show()
    }

    /** The actual logout, in order, with no nested launch (leaving the screen would cancel it). */
    private suspend fun finishLogout() {
        // Tells the server, and clears this phone's push token there.
        safeApiCall("ProfileFragment") { ServiceLocator.api().logout() }

        TokenStore.clear(requireContext())
        ServiceLocator.clearUserData() // cached listings/commitments + the offline queue
        FirebaseAuth.getInstance().signOut()
        try {
            androidx.credentials.CredentialManager.create(requireContext())
                .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("ProfileFragment", "Could not clear credential state: ${e.message}")
        }

        findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}