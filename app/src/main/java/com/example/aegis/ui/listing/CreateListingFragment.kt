package com.example.aegis.ui.listing

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.ServiceLocator
import com.example.aegis.databinding.FragmentCreateListingBinding
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.model.ListingType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CreateListingFragment : AegisBaseFragment(R.layout.fragment_create_listing) {

    private var _binding: FragmentCreateListingBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private val uploadedImages = mutableListOf<String>()

    // Must match the backend's SKILLS list exactly (same as InterestMatchingViewModel.availableSkills).
    private val availableSkills = listOf(
        "Tutoring", "Reading to Children", "Manual Labour", "Cooking or Food Prep",
        "Admin & Organising", "Fundraising", "Marketing & Social Media",
        "Translation or Multilingual", "First Aid"
    )
    private val selectedSkills = mutableSetOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateListingBinding.bind(view)

        setupSpinners()
        setupSkillsPicker()
        setupDateTimePickers()
        setupClickListeners()
    }

    private fun setupSpinners() {
        // Cause Category Spinner - must match the backend's fixed CAUSE_CATEGORIES list exactly.
        val categories = arrayOf(
            "Children & Youth", "Elderly Care", "Soup Kitchens", "Animal Welfare", "Homeless Shelters",
            "Tutoring & Reading", "Environment & Conservation", "Health & Wellbeing", "Disaster Relief",
            "Community Development", "Disability Support"
        )
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        binding.spinnerCategory.adapter = categoryAdapter

        // Listing Type Spinner (SCHEDULED vs ONGOING)
        val listingTypes = ListingType.values().map { it.apiValue }
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listingTypes
        )
        binding.spinnerListingType.adapter = typeAdapter

        binding.spinnerListingType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = ListingType.values()[position]
                binding.layoutScheduledFields.visibility = if (selectedType == ListingType.SCHEDULED) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSkillsPicker() {
        binding.etSkills.apply {
            isFocusable = false              // no keyboard, no typing
            isFocusableInTouchMode = false
            isCursorVisible = false
            setOnClickListener { showSkillsDialog() }
        }
    }

    private fun showSkillsDialog() {
        val items = availableSkills.toTypedArray()
        val checked = BooleanArray(items.size) { items[it] in selectedSkills }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skills required")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                if (isChecked) selectedSkills.add(items[which]) else selectedSkills.remove(items[which])
            }
            .setPositiveButton("Done") { _, _ ->
                binding.etSkills.setText(availableSkills.filter { it in selectedSkills }.joinToString(", "))
            }
            .show()
    }

    private fun setupDateTimePickers() {
        binding.tvDate.setOnClickListener {
            val now = LocalDate.now()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    binding.tvDate.text = selectedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                },
                now.year,
                now.monthValue - 1,
                now.dayOfMonth
            ).show()
        }

        binding.tvTime.setOnClickListener {
            val now = LocalTime.now()
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedTime = LocalTime.of(hourOfDay, minute)
                    binding.tvTime.text = selectedTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
                },
                now.hour,
                now.minute,
                true
            ).show()
        }
    }
    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            submitListing()
        }

        binding.btnDiscard.setOnClickListener {
            clearFields()
        }

        binding.ivCameraBadge.setOnClickListener {
            // Launch photo picker / camera intent logic
            Toast.makeText(requireContext(), "Image selection triggered", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitListing() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val area = binding.etArea.text.toString().trim()
        val minHoursStr = binding.etHours.text.toString().trim()
        val selectedCategory = binding.spinnerCategory.selectedItem.toString()
        val selectedType = ListingType.values()[binding.spinnerListingType.selectedItemPosition]

        // Core Validations
        if (title.isEmpty()) {
            binding.etTitle.error = "Title is required"
            return
        }
        if (description.isEmpty()) {
            binding.etDescription.error = "Description is required"
            return
        }
        if (area.isEmpty()) {
            binding.etArea.error = "Area is required"
            return
        }

        val minHours = minHoursStr.toDoubleOrNull()
        if (minHours == null || minHours < 0) {
            binding.etHours.error = "Enter the minimum hours (e.g. 2)"
            return
        }

        var scheduledInstant: Instant? = null
        var spacesTotal: Int? = null

        if (selectedType == ListingType.SCHEDULED) {
            if (selectedDate == null || selectedTime == null) {
                Toast.makeText(requireContext(), "Please select both date and time for scheduled listings", Toast.LENGTH_SHORT).show()
                return
            }
            val localDateTime = LocalDateTime.of(selectedDate, selectedTime)
            scheduledInstant = localDateTime.atZone(ZoneId.systemDefault()).toInstant()

            val spacesStr = binding.etSpaces.text.toString().trim()
            if (spacesStr.isEmpty()) {
                binding.etSpaces.error = "Spaces total is required for scheduled listings"
                return
            }
            spacesTotal = spacesStr.toIntOrNull()
            if (spacesTotal == null || spacesTotal < 1) {
                binding.etSpaces.error = "Enter a whole number of spaces (1 or more)"
                return
            }
        }

        val skillsList = availableSkills.filter { it in selectedSkills }

        // Construct ListingDraft domain model
        val draft = ListingDraft(
            title = title,
            description = description,
            causeCategories = listOf(selectedCategory),
            skillsRequired = skillsList,
            type = selectedType,
            dateTime = scheduledInstant,
            spacesTotal = spacesTotal,
            area = area,
            minHours = minHours,
            images = uploadedImages
        )

        // Call repository through ServiceLocator
        lifecycleScope.launch {
            val result = ServiceLocator.listingRepository.createListing(draft)
            when (result) {
                is ApiResult.Success -> {
                    Toast.makeText(requireContext(), "Listing created successfully!", Toast.LENGTH_SHORT).show()
                    clearFields()
                }
                is ApiResult.Failure -> {
                    Toast.makeText(requireContext(), "Error: ${result.error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearFields() {
        binding.etTitle.text?.clear()
        binding.etDescription.text?.clear()
        binding.etArea.text?.clear()
        binding.etSkills.text?.clear()
        binding.etSpaces.text?.clear()
        binding.etHours.text?.clear()
        binding.spinnerCategory.setSelection(0)
        binding.spinnerListingType.setSelection(0)
        binding.tvDate.text = "Select Date"
        binding.tvTime.text = "Select Time"
        selectedDate = null
        selectedTime = null
        uploadedImages.clear()
        selectedSkills.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}