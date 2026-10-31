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
            listing.isEnded() -> "ENDED"
            listing.type == ListingType.SCHEDULED -> "UPCOMING"
            else -> "ONGOING"
        }
        binding.tvTitle.text = listing.title
        binding.tvCategory.text = listing.causeCategories.joinToString(", ").ifBlank { "-" }
        binding.tvArea.text = listing.area
        binding.tvDate.text = listing.dateTime
            ?.atZone(ZoneId.systemDefault())
            ?.format(DATE_FORMAT)
            ?: "Ongoing - no set date"
        binding.tvSpaces.text = spacesText(listing)
        binding.tvHours.text = "Minimum ${formatHours(listing.minHours)} h"
        binding.tvSkills.text = listing.skillsRequired.joinToString(", ").ifBlank { "None needed" }
        binding.tvDescription.text = listing.description
    }

    private fun spacesText(l: Listing): String {
        val signed = l.signupCount ?: 0
        return if (l.type == ListingType.SCHEDULED && l.spacesTotal != null) {
            "$signed of ${l.spacesTotal} spaces filled"
        } else {
            "$signed signed up (no cap)"
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
                OrgSignupsFragment.ARG_LISTING_TITLE to listing.title,
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
            "\n\n$signed volunteer${if (signed == 1) " is" else "s are"} signed up. Their sign-ups will be cancelled."
        } else ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete this listing?")
            .setMessage("\"${listing.title}\" will be removed for everyone. This can't be undone.$warning")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete() }
            .setNegativeButton("Keep it", null)
            .show()
    }

    private fun onDeleted() {
        toast("Listing deleted")
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