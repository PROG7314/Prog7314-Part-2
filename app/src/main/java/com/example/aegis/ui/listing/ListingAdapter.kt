package com.example.aegis.ui.listing

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.aegis.R
import com.example.aegis.databinding.ItemListingCardBinding
import com.example.aegis.domain.model.Listing

class ListingAdapter(
    private val onSignUpClick: (Listing) -> Unit
) : ListAdapter<Listing, ListingAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemListingCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listing = getItem(position)
        with(holder.binding) {
            orgName.text = listing.organizationName ?: ""
            listingTitle.text = listing.title
            listingDescription.text = listing.description
            hoursBadge.text = formatHoursBadge(listing.minHours)

            if (listing.images.isNotEmpty()) {
                listingImage.load(listing.images.first()) {
                    placeholder(R.color.aegis_green)
                    error(R.color.aegis_green)
                }
            } else {
                listingImage.setImageDrawable(null)
            }

            signUpButton.setOnClickListener { onSignUpClick(listing) }
        }
    }

    private fun formatHoursBadge(minHours: Double): String {
        val value = if (minHours == minHours.toInt().toDouble()) minHours.toInt().toString() else minHours.toString()
        return "$value hrs commitment"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Listing>() {
            override fun areItemsTheSame(oldItem: Listing, newItem: Listing) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Listing, newItem: Listing) = oldItem == newItem
        }
    }
}