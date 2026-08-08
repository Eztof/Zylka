package com.oliver.zylka.kennzeichen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ItemKennzeichenRegionBinding

/** One row: a [PlateRegion] plus whether it currently counts as "found". */
data class PlateRegionRow(val region: PlateRegion, val found: Boolean)

private val DIFF = object : DiffUtil.ItemCallback<PlateRegionRow>() {
    override fun areItemsTheSame(oldItem: PlateRegionRow, newItem: PlateRegionRow) =
        oldItem.region.code == newItem.region.code

    override fun areContentsTheSame(oldItem: PlateRegionRow, newItem: PlateRegionRow) = oldItem == newItem
}

class PlateRegionAdapter(
    private val onClick: (PlateRegion) -> Unit,
) : ListAdapter<PlateRegionRow, PlateRegionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKennzeichenRegionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(private val binding: ItemKennzeichenRegionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PlateRegionRow, onClick: (PlateRegion) -> Unit) {
            binding.plateBadge.textPlateCountry.text = row.region.country.badgeLetter
            binding.plateBadge.textPlateCode.text = row.region.code
            binding.textName.text = row.region.state?.let { "${row.region.name} · $it" } ?: row.region.name
            binding.iconFound.visibility = if (row.found) View.VISIBLE else View.GONE
            val context = binding.root.context
            val backgroundColor = if (row.found) {
                ContextCompat.getColor(context, R.color.plate_found_background)
            } else {
                ContextCompat.getColor(context, R.color.brand_surface)
            }
            binding.root.setCardBackgroundColor(backgroundColor)
            binding.root.setOnClickListener { onClick(row.region) }
        }
    }
}
