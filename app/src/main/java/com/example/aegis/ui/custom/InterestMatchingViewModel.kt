package com.example.aegis.ui.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.local.UserPreferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InterestMatchingViewModel(private val prefsStore: UserPreferencesDataStore) : ViewModel() {

    val availableCauses = listOf(
        "Children & Youth", "Elderly Care", "Soup Kitchens", "Animal Welfare",
        "Homeless Shelters", "Tutoring & Reading", "Environment & Conservation",
        "Health & Wellbeing", "Disaster Relief", "Community Development", "Disability Support"
    )

    val availableSkills = listOf(
        "Tutoring", "Reading to Children", "Manual Labour", "Cooking or Food Prep",
        "Admin & Organising", "Fundraising", "Marketing & Social Media",
        "Translation or Multilingual", "First Aid"
    )

    private val _selectedCauses = MutableStateFlow<Set<String>>(emptySet())
    val selectedCauses: StateFlow<Set<String>> = _selectedCauses

    private val _selectedSkills = MutableStateFlow<Set<String>>(emptySet())
    val selectedSkills: StateFlow<Set<String>> = _selectedSkills

    init {
        viewModelScope.launch {
            prefsStore.selectedCausesFlow.collect { _selectedCauses.value = it }
        }
        viewModelScope.launch {
            prefsStore.selectedSkillsFlow.collect { _selectedSkills.value = it }
        }
    }

    fun toggleCause(cause: String) {
        val updated = _selectedCauses.value.toMutableSet().apply {
            if (contains(cause)) remove(cause) else add(cause)
        }
        _selectedCauses.value = updated
        viewModelScope.launch { prefsStore.setSelectedCauses(updated) }
    }

    fun toggleSkill(skill: String) {
        val updated = _selectedSkills.value.toMutableSet().apply {
            if (contains(skill)) remove(skill) else add(skill)
        }
        _selectedSkills.value = updated
        viewModelScope.launch { prefsStore.setSelectedSkills(updated) }
    }
}