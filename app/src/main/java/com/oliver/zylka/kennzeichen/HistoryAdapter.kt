package com.oliver.zylka.kennzeichen

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.Discovery
import com.oliver.zylka.databinding.ItemHistoryBinding
import java.util.Locale

private val DIFF = object : DiffUtil.ItemCallback<Discovery>() {
    override fun areItemsTheSame(oldItem: Discovery, newItem: Discovery) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Discovery, newItem: Discovery) = oldItem == newItem
}

class HistoryAdapter(private val currentUid: String?) : ListAdapter<Discovery, HistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), currentUid)

    class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Discovery, currentUid: String?) {
            val context = binding.root.context
            val country = Country.fromId(item.country)
            binding.plateBadge.textPlateCountry.text = country.badgeLetter
            binding.plateBadge.textPlateCode.text = item.code
            binding.textRegionName.text = item.regionName

            val who = if (item.uid == currentUid) context.getString(R.string.kennzeichen_history_you) else item.userLabel
            val whenText = item.discoveredAt?.let {
                DateUtils.getRelativeTimeSpanString(it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            } ?: context.getString(R.string.kennzeichen_history_just_now)
            binding.textMeta.text = context.getString(R.string.kennzeichen_history_meta, who, whenText)

            binding.iconLocation.visibility = if (item.latitude != null && item.longitude != null) View.VISIBLE else View.GONE
            binding.iconLocation.contentDescription = if (item.latitude != null && item.longitude != null) {
                String.format(Locale.getDefault(), "%.2f, %.2f", item.latitude, item.longitude)
            } else {
                null
            }
        }
    }
}
