package com.example.aegis.ui.messages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aegis.databinding.ItemMessageReceivedBinding
import com.example.aegis.databinding.ItemMessageSentBinding
import com.example.aegis.domain.model.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Chat bubbles: the user's own messages on the right, the other person's on the left. */
class MessageAdapter(
    private val otherPartyId: String
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF) {

    private class SentHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)
    private class ReceivedHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) =
        if (getItem(position).senderId == otherPartyId) TYPE_RECEIVED else TYPE_SENT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val time = ChatTime.label(message.sentAt, Instant.now(), ZoneId.systemDefault(), Locale.getDefault())
        when (holder) {
            is SentHolder -> {
                holder.binding.messageText.text = message.text
                holder.binding.messageTime.text = time
            }
            is ReceivedHolder -> {
                holder.binding.messageText.text = message.text
                holder.binding.messageTime.text = time
            }
        }
    }

    private companion object {
        const val TYPE_SENT = 0
        const val TYPE_RECEIVED = 1
        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
        }
    }
}
