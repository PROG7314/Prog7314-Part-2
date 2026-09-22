package com.example.aegis.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.aegis.R
import com.example.aegis.databinding.ItemOrgListingCardBinding
import com.example.aegis.domain.model.Listing

class OrgListingAdapter(
    private val onViewDetailsClick: (Listing) -> Unit
) : ListAdapter<Listing, OrgListingAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemOrgListingCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrgListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listing = getItem(position)
        with(holder.binding) {
            listingTitle.text = listing.title
            signupCountNumber.text = (listing.signupCount ?: 0).toString()

            if (listing.isEnded()) {
                statusBadge.text = root.context.getString(R.string.status_ended)
                statusBadge.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(root.context, R.color.aegis_badge_ended)
                    )
            } else {
                statusBadge.text = root.context.getString(R.string.status_upcoming)
                statusBadge.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(root.context, R.color.aegis_badge_background)
                    )
            }

            if (listing.images.isNotEmpty()) {
                listingImage.load(listing.images.first()) {
                    placeholder(R.color.aegis_green)
                    error(R.color.aegis_green)
                }
            } else {
                listingImage.setImageDrawable(null)
            }

            viewDetailsButton.setOnClickListener { onViewDetailsClick(listing) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Listing>() {
            override fun areItemsTheSame(oldItem: Listing, newItem: Listing) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Listing, newItem: Listing) = oldItem == newItem
        }
    }
}