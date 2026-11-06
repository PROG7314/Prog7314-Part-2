package com.example.aegis.ui.custom

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.UserPreferencesDataStore
import com.example.aegis.domain.model.ApiResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SkillsFragment : AegisBaseFragment() {

    private lateinit var viewModel: InterestMatchingViewModel
    private val skillChips = mutableMapOf<String, Chip>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_skills, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefsStore = UserPreferencesDataStore(requireContext())
        viewModel = InterestMatchingViewModel(prefsStore)

        val skillsGroup = view.findViewById<ChipGroup>(R.id.skillsChipGroup)
        viewModel.availableSkills.forEach { skill ->
            skillChips[skill] = createPillChip(requireContext(), skill, skillsGroup) { viewModel.toggleSkill(skill) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedSkills.collect { selected ->
                    syncPillChips(skillChips, selected) { skill -> viewModel.toggleSkill(skill) }
                }
            }
        }

        val finish = { finishOnboarding() }
        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener { finish() }
        view.findViewById<MaterialButton>(R.id.skipButton).setOnClickListener { finish() }
    }

    // Pushes the volunteer's selected causes/skills to the backend. If the save fails the user is told
    // why and can retry or continue anyway: onboarding shouldn't trap them, and Edit profile can change
    // these choices later.
    private fun finishOnboarding() {
        val causes = viewModel.selectedCauses.value.toList()
        val skills = viewModel.selectedSkills.value.toList()
        setButtonsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = ServiceLocator.userRepository.updateInterests(causes, skills)) {
                is ApiResult.Success -> goHome()
                is ApiResult.Failure -> {
                    Log.w(TAG, "Saving interests failed: ${result.error.message}")
                    showSaveFailed(result.error.message)
                }
            }
        }
    }

    private fun showSaveFailed(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.onboarding_save_failed_title)
            .setMessage(getString(R.string.onboarding_save_failed_message, message))
            .setCancelable(false)
            .setPositiveButton(R.string.retry) { _, _ -> finishOnboarding() }
            .setNegativeButton(R.string.onboarding_continue_anyway) { _, _ -> goHome() }
            .show()
        setButtonsEnabled(true)
    }

    private fun goHome() {
        findNavController().navigate(R.id.action_skillsFragment_to_mainFragment)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        view?.findViewById<MaterialButton>(R.id.nextButton)?.isEnabled = enabled
        view?.findViewById<MaterialButton>(R.id.skipButton)?.isEnabled = enabled
    }

    private companion object {
        const val TAG = "SkillsFragment"
    }
}