package com.example.aegis.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.UserPreferencesDataStore
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import android.widget.CompoundButton
import android.widget.Toast
import com.example.aegis.ui.auth.BiometricHelper

class SettingsFragment : AegisBaseFragment() {

    private lateinit var viewModel: SettingsViewModel

    private val languageNames = listOf("English", "Afrikaans", "Zulu")
    private val languageCodes = listOf("en", "af", "zu")


    private val biometricListener = CompoundButton.OnCheckedChangeListener { button, checked ->
        val prefs = UserPreferencesDataStore(requireContext())
        if (!checked) {
            viewLifecycleOwner.lifecycleScope.launch { prefs.setBiometricEnabled(false) }
            return@OnCheckedChangeListener
        }
        if (!BiometricHelper.isAvailable(requireContext())) {
            Toast.makeText(requireContext(), R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
            setBiometricSwitch(button, false)
            return@OnCheckedChangeListener
        }
        // Confirm with a scan before enabling, so the user can't lock themselves out.
        BiometricHelper.prompt(
            requireActivity(),
            getString(R.string.biometric_title),
            getString(R.string.biometric_subtitle),
            getString(android.R.string.cancel),
            onSuccess = { viewLifecycleOwner.lifecycleScope.launch { prefs.setBiometricEnabled(true) } },
            onFailure = { setBiometricSwitch(button, false) }
        )
    }

    private fun setBiometricSwitch(button: CompoundButton, checked: Boolean) {
        button.setOnCheckedChangeListener(null)
        button.isChecked = checked
        button.setOnCheckedChangeListener(biometricListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefsStore = UserPreferencesDataStore(requireContext())
        viewModel = SettingsViewModel(prefsStore)

        val darkModeSwitch = view.findViewById<MaterialSwitch>(R.id.darkModeSwitch)
        val notificationsSwitch = view.findViewById<MaterialSwitch>(R.id.notificationsSwitch)
        val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)
        val lastSyncText = view.findViewById<TextView>(R.id.lastSyncText)
        val biometricSwitch = view.findViewById<MaterialSwitch>(R.id.biometricSwitch)
        biometricSwitch.setOnCheckedChangeListener(biometricListener)

        languageSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            languageNames
        )

        darkModeSwitch.setOnCheckedChangeListener { _, checked -> viewModel.toggleDarkMode(checked) }
        notificationsSwitch.setOnCheckedChangeListener { _, checked -> viewModel.toggleNotifications(checked) }
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, itemView: View?, position: Int, id: Long) {
                viewModel.setLanguage(languageCodes[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.darkMode.collect { enabled ->
                        if (darkModeSwitch.isChecked != enabled) {
                            darkModeSwitch.setOnCheckedChangeListener(null)
                            darkModeSwitch.isChecked = enabled
                            darkModeSwitch.setOnCheckedChangeListener { _, checked -> viewModel.toggleDarkMode(checked) }
                        }
                    }
                }
                launch {
                    viewModel.notificationsEnabled.collect { enabled ->
                        if (notificationsSwitch.isChecked != enabled) {
                            notificationsSwitch.setOnCheckedChangeListener(null)
                            notificationsSwitch.isChecked = enabled
                            notificationsSwitch.setOnCheckedChangeListener { _, checked -> viewModel.toggleNotifications(checked) }
                        }
                    }
                }
                launch {
                    viewModel.language.collect { code ->
                        val index = languageCodes.indexOf(code).coerceAtLeast(0)
                        if (languageSpinner.selectedItemPosition != index) {
                            languageSpinner.setSelection(index)
                        }
                    }
                }
                launch {
                    // "Last synced: 27 Oct 2026, 15:20" or "Not synced yet"; updates itself when a sync finishes.
                    ServiceLocator.syncState.lastSyncAt.collect {
                        lastSyncText.text = ServiceLocator.syncState.lastSyncLabel(requireContext())
                    }
                }

                launch {
                    prefsStore.biometricEnabledFlow.collect { enabled ->
                        if (biometricSwitch.isChecked != enabled) setBiometricSwitch(biometricSwitch, enabled)
                    }
                }
            }

        }
    }
}