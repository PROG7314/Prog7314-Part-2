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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentCommitmentsBinding
import com.example.aegis.domain.model.Signup
import kotlinx.coroutines.launch

class CommitmentsFragment : AegisBaseFragment() {

    private var _binding: FragmentCommitmentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommitmentsViewModel by viewModels { CommitmentsViewModel.Factory }
    private lateinit var adapter: CommitmentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommitmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CommitmentAdapter(onDismissClick = { signup -> viewModel.dismiss(signup.id) })
        binding.commitmentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.commitmentsRecyclerView.adapter = adapter

        binding.retryButton.setOnClickListener { viewModel.loadCommitments() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.commitments.collect { state -> render(state) } }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun render(state: UiState<List<Signup>>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.commitmentsRecyclerView.visibility = View.GONE

        when (state) {
            is UiState.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
            is UiState.Empty -> binding.emptyState.visibility = View.VISIBLE
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
            }
            is UiState.Success -> {
                binding.commitmentsRecyclerView.visibility = View.VISIBLE
                adapter.submitList(state.data)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}