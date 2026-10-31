package com.example.aegis.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aegis.R
import com.example.aegis.databinding.ItemSignupBinding
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.SignupStatus

/** 3.0 -> "3", 2.5 -> "2.5". */
fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()

class SignupAdapter(
    private val onActionClick: (Signup) -> Unit
) : ListAdapter<Signup, SignupAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemSignupBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSignupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val signup = getItem(position)
        val context = holder.itemView.context
        val b = holder.binding

        b.volunteerName.text = signup.volunteerName ?: context.getString(R.string.signups_unknown_volunteer)

        when (signup.status) {
            SignupStatus.COMPLETED -> {
                b.statusBadge.text = context.getString(R.string.signups_status_completed)
                b.hoursText.visibility = View.VISIBLE
                b.hoursText.text = context.getString(
                    R.string.signups_hours_format,
                    formatHours(signup.hoursCompleted ?: 0.0)
                )
                b.actionButton.visibility = View.VISIBLE
                b.actionButton.text = context.getString(R.string.signups_edit_hours)
            }
            SignupStatus.CONFIRMED -> {
                b.statusBadge.text = context.getString(R.string.signups_status_confirmed)
                b.hoursText.visibility = View.GONE
                b.actionButton.visibility = View.VISIBLE
                b.actionButton.text = context.getString(R.string.signups_mark_complete)
            }
            else -> { // cancelled / unknown: nothing to do
                b.statusBadge.text = signup.status.name
                b.hoursText.visibility = View.GONE
                b.actionButton.visibility = View.GONE
            }
        }
        b.actionButton.setOnClickListener { onActionClick(signup) }
    }

    private object Diff : DiffUtil.ItemCallback<Signup>() {
        override fun areItemsTheSame(old: Signup, new: Signup) = old.id == new.id
        override fun areContentsTheSame(old: Signup, new: Signup) = old == new
    }
}