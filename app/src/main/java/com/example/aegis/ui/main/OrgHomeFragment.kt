package com.example.aegis.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentOrgHomeBinding
import com.example.aegis.domain.model.Listing
import kotlinx.coroutines.launch

class OrgHomeFragment : AegisBaseFragment() {

    private var _binding: FragmentOrgHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrgHomeViewModel by viewModels { OrgHomeViewModel.Factory }
    private lateinit var adapter: OrgListingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrgHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOrgHomeBinding.bind(view)

        adapter = OrgListingAdapter(onViewDetailsClick = ::onViewDetails)
        binding.listingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.listingsRecyclerView.adapter = adapter

        binding.retryButton.setOnClickListener { viewModel.loadListings() }
        binding.createListingButton.setOnClickListener {
            findNavController().navigate(R.id.action_orgHomeFragment_to_createListingFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.listings.collect { state -> render(state) }
            }
        }
    }

    private fun onViewDetails(listing: Listing) {
        Toast.makeText(requireContext(), "View details: ${listing.title}", Toast.LENGTH_SHORT).show()
    }

    private fun render(state: UiState<List<Listing>>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.listingsRecyclerView.visibility = View.GONE

        when (state) {
            is UiState.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
            is UiState.Empty -> binding.emptyState.visibility = View.VISIBLE
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
            }
            is UiState.Success -> {
                binding.listingsRecyclerView.visibility = View.VISIBLE
                adapter.submitList(state.data)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}