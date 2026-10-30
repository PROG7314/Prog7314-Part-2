package com.example.aegis.ui.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.databinding.FragmentMessagesBinding
import com.example.aegis.domain.model.Conversation
import com.example.aegis.ui.main.UiState
import kotlinx.coroutines.launch

/** The inbox tab. */
class MessagesFragment : AegisBaseFragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MessagesViewModel by viewModels { MessagesViewModel.Factory }
    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ConversationAdapter { conversation -> openChat(conversation) }
        binding.conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationsRecyclerView.adapter = adapter
        binding.retryButton.setOnClickListener { viewModel.load() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Reload every time the tab comes back into view, so unread counts are current
                // after reading a chat.
                viewModel.load()
                viewModel.conversations.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: UiState<List<Conversation>>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        binding.conversationsRecyclerView.visibility = View.GONE

        when (state) {
            is UiState.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
            is UiState.Empty -> binding.emptyState.visibility = View.VISIBLE
            is UiState.Error -> {
                binding.errorState.visibility = View.VISIBLE
                binding.errorMessage.text = state.error.message
            }
            is UiState.Success -> {
                binding.conversationsRecyclerView.visibility = View.VISIBLE
                adapter.submitList(state.data)
            }
        }
    }

    private fun openChat(conversation: Conversation) {
        findNavController().navigate(R.id.chatFragment, ChatFragment.args(conversation))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
