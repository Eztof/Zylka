package com.oliver.zylka.waste

import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.data.waste.WasteEvent
import com.oliver.zylka.databinding.ItemWasteEventBinding
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chronological list of upcoming waste-pickup dates, each with its type(s) shown as
 * colored text matching the real bin colors. */
class WasteEventAdapter : RecyclerView.Adapter<WasteEventAdapter.ViewHolder>() {

    private var events: List<WasteEvent> = emptyList()
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy", Locale.GERMANY)

    fun submitList(newEvents: List<WasteEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWasteEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(events[position], dateFormatter)
    }

    override fun getItemCount(): Int = events.size

    class ViewHolder(private val binding: ItemWasteEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: WasteEvent, dateFormatter: DateTimeFormatter) {
            binding.textDate.text = event.date.format(dateFormatter)

            val builder = SpannableStringBuilder()
            event.types.forEachIndexed { index, type ->
                if (index > 0) builder.append("  ·  ")
                val start = builder.length
                builder.append(binding.root.context.getString(type.label))
                val color = ContextCompat.getColor(binding.root.context, type.color)
                builder.setSpan(ForegroundColorSpan(color), start, builder.length, 0)
            }
            binding.textTypes.text = builder
        }
    }
}
