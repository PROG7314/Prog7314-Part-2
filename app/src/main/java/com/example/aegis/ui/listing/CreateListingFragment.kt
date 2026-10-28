package com.example.aegis.ui.listing

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentCreateListingBinding
import com.example.aegis.domain.model.ListingType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CreateListingFragment : AegisBaseFragment(R.layout.fragment_create_listing) {

    private var _binding: FragmentCreateListingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateListingViewModel by viewModels { CreateListingViewModel.Factory }

    // Up to 3 photos from the system photo picker (falls back to the gallery on older phones).
    private val pickPhotos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CreateListingViewModel.MAX_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.setImages(uris)
    }

    // Must match the backend's SKILLS list exactly (same as InterestMatchingViewModel.availableSkills).
    private val availableSkills = listOf(
        "Tutoring", "Reading to Children", "Manual Labour", "Cooking or Food Prep",
        "Admin & Organising", "Fundraising", "Marketing & Social Media",
        "Translation or Multilingual", "First Aid"
    )

    // Must match the backend's fixed CAUSE_CATEGORIES list exactly.
    private val categories = arrayOf(
        "Children & Youth", "Elderly Care", "Soup Kitchens", "Animal Welfare", "Homeless Shelters",
        "Tutoring & Reading", "Environment & Conservation", "Health & Wellbeing", "Disaster Relief",
        "Community Development", "Disability Support"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateListingBinding.bind(view)

        setupSpinners()
        setupSkillsPicker()
        setupDateTimePickers()
        setupClickListeners()
        restorePickedValues()
        collectViewModel()
    }

    // ------------------------------------------------------------------ setup
    private fun setupSpinners() {
        binding.spinnerCategory.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, categories
        )

        binding.spinnerListingType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            ListingType.values().map { it.apiValue }
        )
        binding.spinnerListingType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Ongoing listings have no date and no cap.
                binding.layoutScheduledFields.visibility =
                    if (ListingType.values()[position] == ListingType.SCHEDULED) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSkillsPicker() {
        binding.etSkills.apply {
            isFocusable = false // a tap opens the list; there is nothing to type
            isFocusableInTouchMode = false
            isCursorVisible = false
            setOnClickListener { showSkillsDialog() }
        }
    }

    private fun showSkillsDialog() {
        val items = availableSkills.toTypedArray()
        val checked = BooleanArray(items.size) { items[it] in viewModel.skills }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Skills required")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                if (isChecked) viewModel.skills.add(items[which]) else viewModel.skills.remove(items[which])
            }
            .setPositiveButton("Done") { _, _ -> binding.etSkills.setText(skillsText()) }
            .show()
    }

    private fun skillsText() = availableSkills.filter { it in viewModel.skills }.joinToString(", ")

    private fun setupDateTimePickers() {
        binding.tvDate.setOnClickListener {
            val start = viewModel.date ?: LocalDate.now()
            val dialog = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val picked = LocalDate.of(year, month + 1, dayOfMonth)
                    viewModel.date = picked
                    binding.tvDate.text = picked.format(DateTimeFormatter.ISO_LOCAL_DATE)
                },
                start.year,
                start.monthValue - 1,
                start.dayOfMonth
            )
            dialog.datePicker.minDate = System.currentTimeMillis() - 1000 // no past dates
            dialog.show()
        }

        binding.tvTime.setOnClickListener {
            val start = viewModel.time ?: LocalTime.now()
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val picked = LocalTime.of(hourOfDay, minute)
                    viewModel.time = picked
                    binding.tvTime.text = picked.format(TIME_FORMAT)
                },
                start.hour,
                start.minute,
                true
            ).show()
        }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener { viewModel.save(readForm()) }

        binding.btnDiscard.setOnClickListener {
            viewModel.reset()
            clearFields()
        }

        // Tapping the camera (or the photo) opens the picker; a new choice replaces the old one.
        val openPicker = View.OnClickListener {
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.ivCameraBadge.setOnClickListener(openPicker)
        binding.ivListingCover.setOnClickListener(openPicker)

        binding.btnTabMyListings.setOnClickListener { goToMyListings() }
    }

    /** After a rotation the ViewModel still has the picked date/time/skills; show them again. */
    private fun restorePickedValues() {
        viewModel.date?.let { binding.tvDate.text = it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
        viewModel.time?.let { binding.tvTime.text = it.format(TIME_FORMAT) }
        if (viewModel.skills.isNotEmpty()) binding.etSkills.setText(skillsText())
    }

    private fun readForm() = ListingFormInput(
        title = binding.etTitle.text.toString(),
        description = binding.etDescription.text.toString(),
        area = binding.etArea.text.toString(),
        minHoursText = binding.etHours.text.toString(),
        type = ListingType.values()[binding.spinnerListingType.selectedItemPosition],
        category = binding.spinnerCategory.selectedItem.toString(),
        skills = availableSkills.filter { it in viewModel.skills },
        date = viewModel.date,
        time = viewModel.time,
        spacesText = binding.etSpaces.text.toString()
    )

    // ------------------------------------------------------------------ ViewModel -> screen
    private fun collectViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.images.collect { renderImages(it) } }
                launch { viewModel.saving.collect { renderSaving(it) } }
                launch { viewModel.formErrors.collect { showFormError(it) } }
                launch { viewModel.messages.collect { toast(it, long = true) } }
                launch { viewModel.created.collect { onCreated() } }
            }
        }
    }

    private fun renderImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) {
            binding.ivListingCover.setImageDrawable(null)
            binding.tvPhotoCount.text = "Add up to ${CreateListingViewModel.MAX_IMAGES} photos"
        } else {
            binding.ivListingCover.load(uris.first())
            binding.tvPhotoCount.text = if (uris.size == 1) "1 photo" else "${uris.size} photos"
        }
    }

    private fun renderSaving(saving: Boolean) {
        binding.btnSave.isEnabled = !saving
        binding.btnDiscard.isEnabled = !saving
        binding.btnSave.text = if (saving) "Saving..." else "Save"
    }

    private fun showFormError(error: CreateListingViewModel.FormError) {
        val box = when (error.field) {
            FormField.TITLE -> binding.etTitle
            FormField.DESCRIPTION -> binding.etDescription
            FormField.AREA -> binding.etArea
            FormField.SPACES -> binding.etSpaces
            FormField.HOURS -> binding.etHours
            FormField.DATE_TIME -> null // plain text rows, not boxes: a Toast says it best
        }
        if (box == null) {
            toast(error.message)
        } else {
            box.error = error.message
            box.requestFocus() // also scrolls it into view
        }
    }

    private fun onCreated() {
        toast("Listing created!")
        clearFields()
        // Tell Org Home to reload, so the new listing is there when we land on it.
        runCatching {
            findNavController().getBackStackEntry(R.id.orgHomeFragment).savedStateHandle[LISTING_CHANGED] = true
        }
        goToMyListings()
    }

    /** The "My Listings" tab: the organization's home already lists its listings. */
    private fun goToMyListings() {
        val nav = findNavController()
        if (!nav.popBackStack(R.id.orgHomeFragment, false)) nav.navigate(R.id.orgHomeFragment)
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
        binding.tvDate.text = "" // empty shows the "Select Date" hint again
        binding.tvTime.text = ""
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(requireContext(), message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Key OrgHomeFragment watches (see SETUP-LISTING.md). */
        const val LISTING_CHANGED = "listing_changed"
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}