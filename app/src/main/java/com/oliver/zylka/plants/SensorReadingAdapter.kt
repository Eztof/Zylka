package com.oliver.zylka.plants

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.SensorReading
import com.oliver.zylka.databinding.ItemSensorReadingBinding
import java.text.SimpleDateFormat
import java.util.Locale

/** Chronik aller Messwerte eines Sensors, neueste zuerst. */
class SensorReadingAdapter : RecyclerView.Adapter<SensorReadingAdapter.ViewHolder>() {

    private var readings: List<SensorReading> = emptyList()
    private val dateFormat = SimpleDateFormat("EEE, d. MMMM yyyy, HH:mm", Locale.GERMANY)

    fun submitList(newReadings: List<SensorReading>) {
        readings = newReadings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSensorReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(readings[position], dateFormat)

    override fun getItemCount(): Int = readings.size

    class ViewHolder(private val binding: ItemSensorReadingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reading: SensorReading, dateFormat: SimpleDateFormat) {
            val context = binding.root.context
            binding.textReadingTime.text = reading.measuredAt?.let { dateFormat.format(it) }
                ?: context.getString(R.string.watering_history_unknown_time)
            binding.textReadingValues.text = context.getString(
                R.string.sensor_reading_values,
                reading.temperatureC,
                reading.humidityPercent,
            )
        }
    }
}
