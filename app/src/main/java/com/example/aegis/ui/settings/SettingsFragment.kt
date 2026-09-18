package com.example.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.aegis.core.AegisBaseFragment
import com.example.app.data.local.UserPreferencesDataStore

class SettingsFragment : AegisBaseFragment() {

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO: inflate fragment_settings.xml layout once designed
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefsStore = UserPreferencesDataStore(requireContext())
        viewModel = SettingsViewModel(prefsStore)
        // TODO: bind switches/toggles to viewModel.darkMode, notificationsEnabled, etc.
    }
}