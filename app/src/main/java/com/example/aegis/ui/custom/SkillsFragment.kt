package com.example.aegis.ui.custom

import android.os.Bundle
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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

    // Pushes the volunteer's selected causes/skills to the backend, then heads into the app
    // regardless of whether the save succeeded - onboarding shouldn't trap the user, and the
    // feed already tolerates a missing profile (VolunteerHomeViewModel.loadFeed).
    private fun finishOnboarding() {
        val causes = viewModel.selectedCauses.value.toList()
        val skills = viewModel.selectedSkills.value.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.userRepository.updateInterests(causes, skills)
            findNavController().navigate(R.id.action_skillsFragment_to_mainFragment)
        }
    }
}
