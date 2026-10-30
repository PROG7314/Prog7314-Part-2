package com.example.aegis.ui.listing

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.aegis.data.ImageUploader
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
import com.example.aegis.core.Taxonomy

class CreateListingFragment : AegisBaseFragment(R.layout.fragment_create_listing) {

    private var _binding: FragmentCreateListingBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private val uploadedImages = mutableListOf<String>()

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadCover(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateListingBinding.bind(view)

        setupSpinners()
        setupDateTimePickers()
        setupClickListeners()
    }

    // API values behind the category spinner, in the same order as its (translated) rows.
    private lateinit var categoryValues: Array<String>

    private fun setupSpinners() {
        // Cause Category Spinner - must match the backend's fixed CAUSE_CATEGORIES list exactly.
        // The English text is what the API stores; the spinner only shows its translation.
        val categories = arrayOf(
            "Children & Youth", "Elderly Care", "Soup Kitchens", "Animal Welfare", "Homeless Shelters",
            "Tutoring & Reading", "Environment & Conservation", "Health & Wellbeing", "Disaster Relief",
            "Community Development", "Disability Support"
        )
        categoryValues = categories
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories.map { Taxonomy.label(requireContext(), it) }
        )
        binding.spinnerCategory.adapter = categoryAdapter

        // Listing Type Spinner (SCHEDULED vs ONGOING)
        val listingTypes = ListingType.values().map {
            getString(if (it == ListingType.SCHEDULED) R.string.listing_type_scheduled else R.string.listing_type_ongoing)
        }
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

        binding.ivCameraBadge.setOnClickListener { pickPhoto.launch("image/*") }
        binding.ivListingCover.setOnClickListener { pickPhoto.launch("image/*") }
    }

    // Shows the photo straight away, uploads it, and keeps the saved URL for the listing.
    private fun uploadCover(uri: Uri) {
        binding.ivListingCover.setImageURI(uri)
        setUploading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = ImageUploader.upload(requireContext(), uri, "listing_images")
                uploadedImages.clear()
                uploadedImages.add(url)
            } catch (e: Exception) {
                uploadedImages.clear()
                binding.ivListingCover.setImageDrawable(null)
                Toast.makeText(requireContext(), getString(R.string.photo_upload_failed), Toast.LENGTH_LONG).show()
            } finally {
                _binding?.let { setUploading(false) }
            }
        }
    }

    private fun setUploading(uploading: Boolean) {
        binding.btnSave.isEnabled = !uploading
        binding.ivCameraBadge.isEnabled = !uploading
        binding.ivCameraBadge.alpha = if (uploading) 0.5f else 1f
        if (uploading) Toast.makeText(requireContext(), getString(R.string.photo_uploading), Toast.LENGTH_SHORT).show()
    }

    private fun submitListing() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val area = binding.etArea.text.toString().trim()
        val minHoursStr = binding.etHours.text.toString().trim()
        val skillsStr = binding.etSkills.text.toString().trim()
        val selectedCategory = categoryValues[binding.spinnerCategory.selectedItemPosition]
        val selectedType = ListingType.values()[binding.spinnerListingType.selectedItemPosition]

        // Core Validations
        if (title.isEmpty()) {
            binding.etTitle.error = getString(R.string.error_title_required)
            return
        }
        if (description.isEmpty()) {
            binding.etDescription.error = getString(R.string.error_description_required)
            return
        }
        if (area.isEmpty()) {
            binding.etArea.error = getString(R.string.error_area_required)
            return
        }

        var scheduledInstant: Instant? = null
        var spacesTotal: Int? = null

        if (selectedType == ListingType.SCHEDULED) {
            if (selectedDate == null || selectedTime == null) {
                Toast.makeText(requireContext(), getString(R.string.error_date_time_required), Toast.LENGTH_SHORT).show()
                return
            }
            val localDateTime = LocalDateTime.of(selectedDate, selectedTime)
            scheduledInstant = localDateTime.atZone(ZoneId.systemDefault()).toInstant()

            val spacesStr = binding.etSpaces.text.toString().trim()
            if (spacesStr.isEmpty()) {
                binding.etSpaces.error = getString(R.string.error_spaces_required)
                return
            }
            spacesTotal = spacesStr.toIntOrNull()
        }

        val skillsList = if (skillsStr.isNotEmpty()) {
            skillsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        val minHours = minHoursStr.toDoubleOrNull() ?: 0.0

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
                    Toast.makeText(requireContext(), getString(R.string.listing_created), Toast.LENGTH_SHORT).show()
                    clearFields()
                }
                is ApiResult.Failure -> {
                    Toast.makeText(requireContext(), getString(R.string.listing_create_failed, result.error.message), Toast.LENGTH_LONG).show()
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
        binding.tvDate.text = getString(R.string.hint_select_date)
        binding.tvTime.text = getString(R.string.hint_select_time)
        selectedDate = null
        selectedTime = null
        uploadedImages.clear()
        binding.ivListingCover.setImageDrawable(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}