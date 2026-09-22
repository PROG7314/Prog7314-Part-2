package com.example.aegis.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentVolunteerHomeBinding
import com.example.aegis.ui.listing.ListingAdapter
import kotlinx.coroutines.launch

class VolunteerHomeFragment : AegisBaseFragment() {

    private var _binding: FragmentVolunteerHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VolunteerHomeViewModel by viewModels { VolunteerHomeViewModel.Factory }
    private lateinit var adapter: ListingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVolunteerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVolunteerHomeBinding.bind(view)

        adapter = ListingAdapter(onSignUpClick = { listing -> viewModel.signUp(listing.id) })
        binding.listingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.listingsRecyclerView.adapter = adapter

        binding.retryButton.setOnClickListener { viewModel.loadFeed() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.welcomeName.collect { name ->
                        val firstName = name.substringBefore(" ").ifBlank { null }
                        binding.welcomeText.text = if (firstName != null) {
                            getString(R.string.welcome_back, firstName)
                        } else {
                            ""
                        }
                    }
                }
                launch {
                    viewModel.feed.collect { state -> render(state) }
                }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun render(state: UiState<List<com.example.aegis.domain.model.Listing>>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.listingsRecyclerView.visibility = View.GONE
        binding.subheadline.visibility = View.VISIBLE

        when (state) {
            is UiState.Loading -> {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.subheadline.visibility = View.GONE
            }
            is UiState.Empty -> {
                binding.emptyState.visibility = View.VISIBLE
                binding.subheadline.text = getString(R.string.new_listings_count, 0)
            }
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
                binding.subheadline.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.listingsRecyclerView.visibility = View.VISIBLE
                adapter.submitList(state.data)
                binding.subheadline.text = getString(R.string.new_listings_count, state.data.size)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}