package com.example.aegis.ui.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.data.ServiceLocator
import com.example.aegis.databinding.FragmentChatBinding
import com.example.aegis.domain.model.Conversation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One conversation. Opened from the inbox, or from a commitment's Message button. */
class ChatFragment : AegisBaseFragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels {
        viewModelFactory {
            initializer {
                ChatViewModel(
                    conversationId = requireArguments().getString(ARG_CONVERSATION_ID).orEmpty(),
                    otherPartyId = requireArguments().getString(ARG_OTHER_ID).orEmpty(),
                    messages = ServiceLocator.messageRepository
                )
            }
        }
    }
    private lateinit var adapter: MessageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        binding.chatTitle.text = args.getString(ARG_OTHER_NAME) ?: getString(R.string.unknown_person)
        binding.chatSubtitle.text = getString(R.string.conversation_about, args.getString(ARG_LISTING_TITLE).orEmpty())

        // The bottom bar is hidden here, so keep the header clear of the status bar and the
        // message box above the keyboard / navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }

        adapter = MessageAdapter(viewModel.otherPartyId)
        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.messagesRecyclerView.adapter = adapter

        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.sendButton.setOnClickListener { send() }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ChatViewModel.Event.Sent -> binding.messageInput.text?.clear()
                            is ChatViewModel.Event.SendFailed ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Check for new messages while the chat is on screen. Push notifications can
                // replace this once FCM is in.
                launch {
                    while (true) {
                        viewModel.refresh()
                        delay(REFRESH_MS)
                    }
                }
            }
        }
    }

    private fun send() = viewModel.send(binding.messageInput.text?.toString())

    private fun render(state: ChatViewModel.ChatState) {
        binding.loadingIndicator.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.errorState.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
        binding.errorMessage.text = state.errorMessage
        val showList = !state.loading && state.errorMessage == null && state.messages.isNotEmpty()
        binding.messagesRecyclerView.visibility = if (showList) View.VISIBLE else View.GONE
        binding.emptyChat.visibility =
            if (!state.loading && state.errorMessage == null && state.messages.isEmpty()) View.VISIBLE else View.GONE
        binding.sendButton.isEnabled = !state.sending

        val firstFill = adapter.itemCount == 0
        val wasAtEnd = !binding.messagesRecyclerView.canScrollVertically(1)
        val lastIsMine = state.messages.lastOrNull()?.let { it.senderId != viewModel.otherPartyId } == true
        adapter.submitList(state.messages) {
            // Follow new messages and the user's own sends, but don't yank the view if they
            // scrolled up to read older ones.
            if (state.messages.isNotEmpty() && (firstFill || wasAtEnd || lastIsMine)) {
                binding.messagesRecyclerView.scrollToPosition(state.messages.lastIndex)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONVERSATION_ID = "conversationId"
        private const val ARG_OTHER_ID = "otherPartyId"
        private const val ARG_OTHER_NAME = "otherPartyName"
        private const val ARG_LISTING_TITLE = "listingTitle"
        private const val REFRESH_MS = 5_000L

        fun args(conversation: Conversation) = bundleOf(
            ARG_CONVERSATION_ID to conversation.id,
            ARG_OTHER_ID to conversation.otherPartyId,
            ARG_OTHER_NAME to conversation.otherPartyName,
            ARG_LISTING_TITLE to conversation.listingTitle
        )
    }
}
