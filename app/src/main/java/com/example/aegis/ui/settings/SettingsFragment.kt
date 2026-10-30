package com.example.aegis.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.local.UserPreferencesDataStore
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class SettingsFragment : AegisBaseFragment() {

    private lateinit var viewModel: SettingsViewModel

    private val languageNames = listOf("English", "Afrikaans", "isiZulu")
    private val languageCodes = listOf("en", "af", "zu")

    // Last language we know is saved. The Spinner reports its initial position as if the user
    // picked it, so only a real change away from this is treated as a choice.
    private var savedLanguage: String? = null

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
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        viewModel = SettingsViewModel(prefsStore, ServiceLocator.userRepository, systemIsDark)

        val darkModeSwitch = view.findViewById<MaterialSwitch>(R.id.darkModeSwitch)
        val notificationsSwitch = view.findViewById<MaterialSwitch>(R.id.notificationsSwitch)
        val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)

        languageSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            languageNames
        )

        darkModeSwitch.setOnCheckedChangeListener { _, checked -> viewModel.toggleDarkMode(checked) }
        notificationsSwitch.setOnCheckedChangeListener { _, checked -> viewModel.toggleNotifications(checked) }
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, itemView: View?, position: Int, id: Long) {
                val code = languageCodes[position]
                if (savedLanguage != null && code != savedLanguage) viewModel.setLanguage(code)
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
                    viewModel.syncFailed.collect { message ->
                        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
                    }
                }
                launch {
                    viewModel.language.collect { code ->
                        savedLanguage = code
                        val index = languageCodes.indexOf(code).coerceAtLeast(0)
                        if (languageSpinner.selectedItemPosition != index) {
                            languageSpinner.setSelection(index)
                        }
                    }
                }
            }
        }
    }
}