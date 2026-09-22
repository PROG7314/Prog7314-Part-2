package com.example.aegis.ui.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.local.UserPreferencesDataStore

class InterestMatchingFragment : AegisBaseFragment(R.layout.fragment_create_listing) {

    private lateinit var viewModel: InterestMatchingViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO: inflate fragment_interest_matching.xml once designed
        // (chip group or checkbox list for causes + skills)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefsStore = UserPreferencesDataStore(requireContext())
        viewModel = InterestMatchingViewModel(prefsStore)
        // TODO: bind availableCauses/availableSkills to chips or checkboxes,
        // call viewModel.toggleCause() / toggleSkill() on selection
    }
}