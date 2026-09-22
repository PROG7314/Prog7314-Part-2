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
import com.example.aegis.data.local.UserPreferencesDataStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class InterestMatchingFragment : AegisBaseFragment() {

    private lateinit var viewModel: InterestMatchingViewModel
    private val causeChips = mutableMapOf<String, Chip>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_interest_matching, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefsStore = UserPreferencesDataStore(requireContext())
        viewModel = InterestMatchingViewModel(prefsStore)

        val causesGroup = view.findViewById<ChipGroup>(R.id.causesChipGroup)
        viewModel.availableCauses.forEach { cause ->
            causeChips[cause] = createPillChip(requireContext(), cause, causesGroup) { viewModel.toggleCause(cause) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedCauses.collect { selected ->
                    syncPillChips(causeChips, selected) { cause -> viewModel.toggleCause(cause) }
                }
            }
        }

        val goToSkills = { findNavController().navigate(R.id.action_interestMatchingFragment_to_skillsFragment) }
        view.findViewById<MaterialButton>(R.id.nextButton).setOnClickListener { goToSkills() }
        view.findViewById<MaterialButton>(R.id.skipButton).setOnClickListener { goToSkills() }
    }
}
