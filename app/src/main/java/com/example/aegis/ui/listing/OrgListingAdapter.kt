package com.example.aegis.ui.listing

import android.content.res.ColorStateList
import java.text.NumberFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.aegis.R
import com.example.aegis.core.Strings
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
            listingTitle.text = listing.titleFor(Strings.languageCode())
            signupCountNumber.text = NumberFormat.getInstance().format(listing.signupCount ?: 0)

            if (listing.isEnded()) {
                statusBadge.text = root.context.getString(R.string.status_ended)
                statusBadge.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(root.context, R.color.badge_ended_background)
                    )
            } else {
                statusBadge.text = root.context.getString(R.string.status_upcoming)
                statusBadge.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(root.context, R.color.badge_background)
                    )
            }

            if (listing.images.isNotEmpty()) {
                listingImage.load(listing.images.first()) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
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