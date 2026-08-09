package com.oliver.zylka.plants

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.PotForecast
import com.oliver.zylka.databinding.ItemPotSummaryBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Eine Karte pro Topf: Name, enthaltene Pflanzen, Vorrats-Fortschritt, Fälligkeit, Button
 * "Gegossen" - Liste ist bereits nach Dringlichkeit sortiert ([com.oliver.zylka.data.plants.PlantForecastRepository]). */
class PotSummaryAdapter(
    private val onWatered: (PotForecast) -> Unit,
    private val onOpenDetail: (PotForecast) -> Unit,
) : RecyclerView.Adapter<PotSummaryAdapter.ViewHolder>() {

    private var forecasts: List<PotForecast> = emptyList()

    fun submitList(newForecasts: List<PotForecast>) {
        forecasts = newForecasts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPotSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onWatered, onOpenDetail)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(forecasts[position])

    override fun getItemCount(): Int = forecasts.size

    class ViewHolder(
        private val binding: ItemPotSummaryBinding,
        private val onWatered: (PotForecast) -> Unit,
        private val onOpenDetail: (PotForecast) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(forecast: PotForecast) {
            val context = binding.root.context
            binding.textPotName.text = forecast.pot.name
            binding.textPlantNames.text = if (forecast.plants.isEmpty()) {
                context.getString(R.string.plants_no_plants_assigned)
            } else {
                forecast.plants.joinToString(", ") { it.name }
            }
            binding.progressVorrat.progress = forecast.percentFull
            binding.textStatus.text = statusText(context, forecast)
            binding.textStatus.setTextColor(ContextCompat.getColor(context, statusColor(forecast)))
            binding.textStaleHint.isVisible = forecast.isStale

            binding.buttonWatered.isEnabled = forecast.hasLocation
            binding.buttonWatered.setOnClickListener { onWatered(forecast) }
            binding.root.setOnClickListener { onOpenDetail(forecast) }
        }

        private fun statusColor(forecast: PotForecast): Int {
            val faelligAb = forecast.faelligAbEpochMillis
            return when {
                !forecast.hasLocation || faelligAb == null -> R.color.brand_outline
                forecast.isOverdue -> R.color.brand_error
                daysUntil(faelligAb) <= 2L -> R.color.brand_secondary
                else -> R.color.brand_primary
            }
        }

        private fun statusText(context: Context, forecast: PotForecast): String {
            val faelligAb = forecast.faelligAbEpochMillis
            return when {
                !forecast.hasLocation -> context.getString(R.string.plants_status_no_location)
                faelligAb == null -> context.getString(R.string.plants_status_unknown)
                forecast.isOverdue -> context.getString(R.string.plants_status_water_now)
                else -> when (val days = daysUntil(faelligAb)) {
                    0L -> context.getString(R.string.plants_status_today)
                    else -> context.getString(R.string.plants_status_due_in_days, days)
                }
            }
        }

        private fun daysUntil(epochMillis: Long): Long {
            val dueDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            return ChronoUnit.DAYS.between(LocalDate.now(), dueDate).coerceAtLeast(0)
        }
    }
}
