package com.example.aegis.ui.hours

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentHoursBinding
import com.example.aegis.domain.model.HoursSummary
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.launch

class HoursFragment : AegisBaseFragment() {

    private var _binding: FragmentHoursBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HoursViewModel by viewModels { HoursViewModel.Factory }
    private lateinit var adapter: HoursAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHoursBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HoursAdapter()
        binding.hoursRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.hoursRecyclerView.adapter = adapter

        binding.retryButton.setOnClickListener { viewModel.loadHours() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hours.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: UiState<HoursSummary>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.hoursRecyclerView.visibility = View.GONE
        binding.subheadline.visibility = View.VISIBLE

        when (state) {
            is UiState.Loading -> {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.subheadline.visibility = View.GONE
            }
            is UiState.Empty -> {
                binding.emptyState.visibility = View.VISIBLE
                binding.subheadline.text = getString(R.string.total_hours_completed, "0")
            }
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
                binding.subheadline.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.hoursRecyclerView.visibility = View.VISIBLE
                adapter.submitList(state.data.entries)
                binding.subheadline.text = getString(R.string.total_hours_completed, formatTotal(state.data.totalHours))
            }
        }
    }

    private fun formatTotal(hours: Double): String =
        if (hours == hours.toInt().toDouble()) hours.toInt().toString() else hours.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}