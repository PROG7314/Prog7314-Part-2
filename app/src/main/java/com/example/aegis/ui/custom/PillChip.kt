package com.example.aegis.ui.custom

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.aegis.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

internal fun createPillChip(context: Context, label: String, group: ChipGroup, onToggle: () -> Unit): Chip {
    val chip = Chip(context)
    chip.text = label
    chip.isCheckable = true
    chip.chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_background_color)
    chip.setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text_color))
    chip.chipStrokeColor = ContextCompat.getColorStateList(context, R.color.brand_green)
    chip.chipStrokeWidth = 1.5f * context.resources.displayMetrics.density
    chip.chipCornerRadius = 20f * context.resources.displayMetrics.density
    chip.setOnCheckedChangeListener { _, _ -> onToggle() }
    group.addView(chip)
    return chip
}

internal fun syncPillChips(chips: Map<String, Chip>, selected: Set<String>, onToggle: (String) -> Unit) {
    chips.forEach { (label, chip) ->
        val shouldBeChecked = selected.contains(label)
        if (chip.isChecked != shouldBeChecked) {
            chip.setOnCheckedChangeListener(null)
            chip.isChecked = shouldBeChecked
            chip.setOnCheckedChangeListener { _, _ -> onToggle(label) }
        }
    }
}
