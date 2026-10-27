package com.example.aegis.ui.main

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentOrgSignupsBinding
import com.example.aegis.domain.model.Signup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * An organization's view of one of its listings: who signed up, and a way to mark each person
 * complete (which is what puts hours on the volunteer's Hours screen).
 * Opened from OrgHomeFragment's "View Details" button with the ARG_* values below.
 */
class OrgSignupsFragment : AegisBaseFragment() {

    private var _binding: FragmentOrgSignupsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrgSignupsViewModel by viewModels { OrgSignupsViewModel.Factory }
    private lateinit var adapter: SignupAdapter

    private val listingId by lazy { requireArguments().getString(ARG_LISTING_ID).orEmpty() }
    private val listingTitle by lazy { requireArguments().getString(ARG_LISTING_TITLE).orEmpty() }
    private val minHours by lazy { requireArguments().getDouble(ARG_MIN_HOURS, 0.0) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrgSignupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.listingTitleHeader.text = listingTitle
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.retryButton.setOnClickListener { viewModel.retry() }

        adapter = SignupAdapter(onActionClick = ::showHoursDialog)
        binding.signupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.signupsRecyclerView.adapter = adapter

        viewModel.load(listingId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.signups.collect { state -> render(state) } }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun render(state: UiState<List<Signup>>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.signupsRecyclerView.visibility = View.GONE

        when (state) {
            is UiState.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
            is UiState.Empty -> binding.emptyState.visibility = View.VISIBLE
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
            }
            is UiState.Success -> {
                binding.signupsRecyclerView.visibility = View.VISIBLE
                adapter.submitList(state.data)
            }
        }
    }

    /** Asks for the hours (pre-filled) and only closes once the number is valid. */
    private fun showHoursDialog(signup: Signup) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.signups_dialog_hint)
            setText(formatHours(signup.hoursCompleted ?: minHours))
            setSelection(text.length)
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.signups_dialog_title, signup.volunteerName ?: getString(R.string.signups_unknown_volunteer)))
            .setView(container)
            .setPositiveButton(R.string.signups_dialog_save, null) // set below so invalid input keeps it open
            .setNegativeButton(R.string.signups_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                // Some South African locales type a comma as the decimal separator.
                val hours = input.text.toString().trim().replace(',', '.').toDoubleOrNull()
                if (hours == null || hours < 0 || hours > 1000) {
                    input.error = getString(R.string.signups_hours_invalid)
                } else {
                    viewModel.complete(signup, hours)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_LISTING_ID = "listingId"
        const val ARG_LISTING_TITLE = "listingTitle"
        const val ARG_MIN_HOURS = "minHours"
    }
}