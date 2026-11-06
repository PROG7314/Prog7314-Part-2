package com.example.aegis.ui.listing

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.core.Strings
import com.example.aegis.databinding.FragmentOrgListingDetailBinding
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingType
import com.example.aegis.ui.main.OrgSignupsFragment
import com.example.aegis.ui.main.UiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One of the organisation's listings: details, plus View sign-ups / Edit / Delete.
 * Opened from Org Home with ARG_LISTING_ID.
 */
class OrgListingDetailFragment : AegisBaseFragment(R.layout.fragment_org_listing_detail) {

    private var _binding: FragmentOrgListingDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrgListingDetailViewModel by viewModels { OrgListingDetailViewModel.Factory }

    private val listingId by lazy { requireArguments().getString(ARG_LISTING_ID).orEmpty() }

    private var current: Listing? = null
    private var photoIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOrgListingDetailBinding.bind(view)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }

        binding.btnViewSignups.setOnClickListener { current?.let(::openSignups) }
        binding.btnEdit.setOnClickListener { current?.let(::openEdit) }
        binding.btnDelete.setOnClickListener { current?.let(::confirmDelete) }
        binding.ivCover.setOnClickListener { showNextPhoto() } // several photos: tap to flip through

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.deleting.collect { binding.btnDelete.isEnabled = !it } }
                launch { viewModel.messages.collect { toast(it) } }
                launch { viewModel.deleted.collect { onDeleted() } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.load(listingId) // also refreshes after coming back from Edit
    }

    // ------------------------------------------------------------------ rendering
    private fun render(state: UiState<Listing>) {
        binding.loading.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        binding.errorState.visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
        binding.content.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE

        when (state) {
            is UiState.Error -> binding.tvError.text = state.error.message
            is UiState.Success -> show(state.data)
            else -> Unit
        }
    }

    private fun show(listing: Listing) {
        current = listing
        photoIndex = 0
        showPhoto(listing)

        binding.tvStatus.text = when {
            listing.isEnded() -> getString(R.string.status_ended)
            listing.type == ListingType.SCHEDULED -> getString(R.string.status_upcoming)
            else -> getString(R.string.badge_ongoing)
        }
        binding.tvTitle.text = listing.titleFor(Strings.languageCode())
        binding.tvCategory.text = listing.causeCategories.joinToString(", ").ifBlank { "-" }
        binding.tvArea.text = listing.area
        binding.tvDate.text = listing.dateTime
            ?.atZone(ZoneId.systemDefault())
            ?.format(DATE_FORMAT)
            ?: getString(R.string.detail_ongoing_no_date)
        binding.tvSpaces.text = spacesText(listing)
        binding.tvHours.text = getString(R.string.detail_hours_min, formatHours(listing.minHours))
        binding.tvSkills.text = listing.skillsRequired.joinToString(", ").ifBlank { getString(R.string.detail_no_skills) }
        binding.tvDescription.text = listing.descriptionFor(Strings.languageCode())
    }

    private fun spacesText(l: Listing): String {
        val signed = l.signupCount ?: 0
        return if (l.type == ListingType.SCHEDULED && l.spacesTotal != null) {
            getString(R.string.detail_spaces_filled, signed, l.spacesTotal)
        } else {
            getString(R.string.detail_signed_up_no_cap, signed)
        }
    }

    private fun showPhoto(listing: Listing) {
        val photos = listing.images
        if (photos.isEmpty()) {
            binding.ivCover.setImageDrawable(null)
            binding.tvPhotoCount.visibility = View.GONE
        } else {
            binding.ivCover.load(photos[photoIndex % photos.size])
            binding.tvPhotoCount.visibility = if (photos.size > 1) View.VISIBLE else View.GONE
            binding.tvPhotoCount.text = "${photoIndex % photos.size + 1} / ${photos.size}"
        }
    }

    private fun showNextPhoto() {
        val listing = current ?: return
        if (listing.images.size < 2) return
        photoIndex++
        showPhoto(listing)
    }

    // ------------------------------------------------------------------ actions
    private fun openSignups(listing: Listing) {
        findNavController().navigate(
            R.id.orgSignupsFragment,
            bundleOf(
                OrgSignupsFragment.ARG_LISTING_ID to listing.id,
                OrgSignupsFragment.ARG_LISTING_TITLE to listing.titleFor(Strings.languageCode()),
                OrgSignupsFragment.ARG_MIN_HOURS to listing.minHours
            )
        )
    }

    private fun openEdit(listing: Listing) {
        findNavController().navigate(
            R.id.createListingFragment,
            bundleOf(CreateListingFragment.ARG_LISTING_ID to listing.id)
        )
    }

    private fun confirmDelete(listing: Listing) {
        val signed = listing.signupCount ?: 0
        val warning = if (signed > 0) {
            "\n\n" + resources.getQuantityString(R.plurals.delete_signups_warning, signed, signed)
        } else ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_listing_title)
            .setMessage(getString(R.string.delete_listing_message, listing.titleFor(Strings.languageCode()), warning))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.keep_it, null)
            .show()
    }

    private fun onDeleted() {
        toast(getString(R.string.listing_deleted))
        runCatching {
            findNavController().getBackStackEntry(R.id.orgHomeFragment)
                .savedStateHandle[CreateListingFragment.LISTING_CHANGED] = true
        }
        findNavController().popBackStack()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun formatHours(hours: Double) =
        if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_LISTING_ID = "listingId"
        private val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm", Locale.getDefault())
    }
}