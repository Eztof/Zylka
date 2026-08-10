package com.oliver.zylka.plants

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.Sensor
import com.oliver.zylka.data.plants.SensorLiveCache
import com.oliver.zylka.databinding.ItemSensorBinding
import java.text.SimpleDateFormat
import java.util.Locale

/** Ein Sensor pro Karte: Name + aktueller Messwert. Zeigt bevorzugt den per BLE dauerhaft
 * gelauschten Live-Wert ([SensorLiveCache]) - erst wenn (noch) keiner da ist, den zuletzt in
 * Firestore gespeicherten. */
class SensorAdapter(private val onClick: (Sensor) -> Unit) : RecyclerView.Adapter<SensorAdapter.ViewHolder>() {

    private var sensors: List<Sensor> = emptyList()

    fun submitList(newSensors: List<Sensor>) {
        sensors = newSensors
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSensorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(sensors[position])

    override fun getItemCount(): Int = sensors.size

    class ViewHolder(
        private val binding: ItemSensorBinding,
        private val onClick: (Sensor) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("d.M., HH:mm", Locale.GERMANY)

        fun bind(sensor: Sensor) {
            val context = binding.root.context
            binding.textSensorName.text = sensor.name
            val live = SensorLiveCache.get(sensor.macAddress)
            binding.textSensorReading.text = when {
                live != null -> context.getString(R.string.sensor_reading_summary_live, live.temperatureC, live.humidityPercent)
                sensor.lastTemperatureC != null && sensor.lastHumidityPercent != null -> {
                    val zeit = sensor.lastMeasuredAt?.let { timeFormat.format(it) }
                    context.getString(R.string.sensor_reading_summary, sensor.lastTemperatureC, sensor.lastHumidityPercent, zeit.orEmpty())
                }
                else -> context.getString(R.string.sensor_no_reading)
            }
            binding.root.setOnClickListener { onClick(sensor) }
        }
    }
}
