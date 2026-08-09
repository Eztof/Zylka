package com.oliver.zylka.plants

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.Watering
import com.oliver.zylka.databinding.ItemWateringHistoryBinding
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chronik aller Gießvorgänge eines Topfs, neueste zuerst. */
class WateringHistoryAdapter : RecyclerView.Adapter<WateringHistoryAdapter.ViewHolder>() {

    private var waterings: List<Watering> = emptyList()
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy, HH:mm", Locale.GERMANY)

    fun submitList(newWaterings: List<Watering>) {
        waterings = newWaterings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWateringHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(waterings[position], dateFormatter)

    override fun getItemCount(): Int = waterings.size

    class ViewHolder(private val binding: ItemWateringHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(watering: Watering, dateFormatter: DateTimeFormatter) {
            val context = binding.root.context
            binding.textDate.text = watering.wateredAt
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.format(dateFormatter)
                ?: context.getString(R.string.watering_history_unknown_time)
            binding.textFeedback.text = watering.feedback?.let { context.getString(it.label) }
                ?: context.getString(R.string.watering_history_no_feedback)
        }
    }
}
