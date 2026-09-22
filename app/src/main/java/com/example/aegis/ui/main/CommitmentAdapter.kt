package com.example.aegis.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aegis.databinding.ItemCommitmentCardBinding
import com.example.aegis.domain.model.Signup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CommitmentAdapter(
    private val onDismissClick: (Signup) -> Unit
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
            commitmentTitle.text = listing?.title ?: "(listing no longer available)"
            commitmentArea.text = listing?.area ?: ""
            dateBadge.text = formatDateBadge(listing?.dateTime)
            timeBadge.text = formatTime(listing?.dateTime)
            timeBadge.visibility = if (listing?.dateTime != null) android.view.View.VISIBLE else android.view.View.GONE

            dismissButton.setOnClickListener { onDismissClick(signup) }
        }
    }


    private fun formatDateBadge(dateTime: Instant?): String {
        if (dateTime == null) return "ONGOING"
        val zone = ZoneId.systemDefault()
        val date = dateTime.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today.plusDays(1) -> "TOMORROW"
            today -> "TODAY"
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