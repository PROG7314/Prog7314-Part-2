package com.example.aegis.ui.commitments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.aegis.databinding.ItemCommitmentCardBinding
import com.example.aegis.domain.model.Signup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.aegis.R
import com.example.aegis.core.Strings

class CommitmentAdapter(
    private val onDismissClick: (Signup) -> Unit,
    private val onMessageClick: (Signup) -> Unit
) : ListAdapter<Signup, CommitmentAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemCommitmentCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommitmentCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val signup = getItem(position)
        val listing = signup.listing
        with(holder.binding) {
            commitmentTitle.text = listing?.title ?: root.context.getString(R.string.listing_unavailable)
            commitmentArea.text = listing?.area ?: ""
            dateBadge.text = formatDateBadge(listing?.dateTime)
            timeBadge.text = formatTime(listing?.dateTime)
            timeBadge.visibility = if (listing?.dateTime != null) View.VISIBLE else View.GONE

            val photo = listing?.images?.firstOrNull()
            if (photo != null) commitmentImage.load(photo) else commitmentImage.setImageDrawable(null)

            dismissButton.setOnClickListener { onDismissClick(signup) }
            // Nobody to message if the organization deleted the listing.
            messageButton.visibility = if (listing != null) View.VISIBLE else View.GONE
            messageButton.setOnClickListener { onMessageClick(signup) }
        }
    }


    private fun formatDateBadge(dateTime: Instant?): String {
        if (dateTime == null) return Strings.get(R.string.badge_ongoing)
        val zone = ZoneId.systemDefault()
        val date = dateTime.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today.plusDays(1) -> Strings.get(R.string.badge_tomorrow)
            today -> Strings.get(R.string.badge_today)
            else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())).uppercase()
        }
    }

    private fun formatTime(dateTime: Instant?): String {
        if (dateTime == null) return ""
        return dateTime.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Signup>() {
            override fun areItemsTheSame(oldItem: Signup, newItem: Signup) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Signup, newItem: Signup) = oldItem == newItem
        }
    }
}