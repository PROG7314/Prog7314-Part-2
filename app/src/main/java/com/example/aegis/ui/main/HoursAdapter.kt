package com.example.aegis.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aegis.databinding.ItemHoursEntryBinding
import com.example.aegis.domain.model.HoursEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HoursAdapter : ListAdapter<HoursEntry, HoursAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemHoursEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHoursEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        with(holder.binding) {
            entryTitle.text = entry.listingTitle
            hoursBadge.text = formatHoursBadge(entry.hours)
            entryDate.text = entry.completedAt
                ?.atZone(ZoneId.systemDefault())
                ?.format(DateTimeFormatter.ofPattern("'Completed' EEE, MMM d", Locale.getDefault()))
                ?: ""
        }
    }

    private fun formatHoursBadge(hours: Double): String {
        val value = if (hours == hours.toInt().toDouble()) hours.toInt().toString() else hours.toString()
        return "$value HRS"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HoursEntry>() {
            override fun areItemsTheSame(oldItem: HoursEntry, newItem: HoursEntry) =
                oldItem.listingTitle == newItem.listingTitle && oldItem.completedAt == newItem.completedAt
            override fun areContentsTheSame(oldItem: HoursEntry, newItem: HoursEntry) = oldItem == newItem
        }
    }
}
