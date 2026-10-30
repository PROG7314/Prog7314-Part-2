package com.example.aegis.ui.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aegis.R
import com.example.aegis.databinding.ItemConversationBinding
import com.example.aegis.domain.model.Conversation
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            partyName.text = conversation.otherPartyName ?: context.getString(R.string.unknown_person)
            listingTitle.text = context.getString(R.string.conversation_about, conversation.listingTitle)
            lastMessage.text = conversation.lastMessage ?: context.getString(R.string.no_messages_preview)
            timeLabel.text = ChatTime.label(conversation.lastMessageAt, Instant.now(), ZoneId.systemDefault(), Locale.getDefault())

            val badge = UnreadBadge.label(conversation.unreadCount)
            unreadBadge.visibility = if (badge != null) View.VISIBLE else View.GONE
            unreadBadge.text = badge
            unreadBadge.contentDescription = context.getString(R.string.unread_count_description, conversation.unreadCount)
            // Unread conversations read as bolder, like most inboxes.
            // Keep the app font: passing null here would fall back to the system font.
            val appFont = androidx.core.content.res.ResourcesCompat.getFont(root.context, com.example.aegis.R.font.plus_jakarta_sans)
            lastMessage.setTypeface(appFont, if (conversation.unreadCount > 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            root.setOnClickListener { onClick(conversation) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation) = oldItem == newItem
        }
    }
}
