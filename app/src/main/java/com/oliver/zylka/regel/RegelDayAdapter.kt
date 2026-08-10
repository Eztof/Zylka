package com.oliver.zylka.regel

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.databinding.ItemRegelDayBinding
import java.time.LocalDate

/** Eine Tageszelle im Monats-Grid - `null` steht für eine Lückenzelle vor dem 1. oder nach dem
 * letzten Tag des Monats (führt das Raster auf ein Vielfaches von 7). [echteIntensitaet] ist ein
 * tatsächlich eingetragener Wert, [prognoseIntensitaet] eine aus [com.oliver.zylka.data.regel.RegelCalculator]
 * abgeleitete Vorhersage - ein echter Eintrag hat immer Vorrang vor einer Prognose. */
data class RegelTagZelle(
    val datum: LocalDate,
    val echteIntensitaet: Int?,
    val prognoseIntensitaet: Int?,
    val istHeute: Boolean,
)

/** Zeichnet das Monats-Grid: gefüllte Zelle (Alpha proportional zur Intensität) für echte
 * Einträge, nur umrandete Zelle für Prognose-Tage - so bleibt eine Vorhersage immer klar von
 * echten Daten unterscheidbar. Jede Zelle ist antippbar (auch Lückenzellen nicht, siehe
 * [submitList]). */
class RegelDayAdapter(private val onDayClick: (LocalDate) -> Unit) : RecyclerView.Adapter<RegelDayAdapter.ViewHolder>() {

    private var zellen: List<RegelTagZelle?> = emptyList()

    fun submitList(newZellen: List<RegelTagZelle?>) {
        zellen = newZellen
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRegelDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onDayClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(zellen[position])

    override fun getItemCount(): Int = zellen.size

    class ViewHolder(
        private val binding: ItemRegelDayBinding,
        private val onDayClick: (LocalDate) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val density = binding.root.context.resources.displayMetrics.density
        private fun dp(value: Float): Int = (value * density).toInt()

        fun bind(zelle: RegelTagZelle?) {
            if (zelle == null) {
                binding.root.isVisible = false
                binding.root.setOnClickListener(null)
                return
            }
            binding.root.isVisible = true
            val context = binding.root.context
            val basisfarbe = ContextCompat.getColor(context, R.color.regel_primary)
            val oberflaeche = ContextCompat.getColor(context, R.color.brand_surface)
            val oberflaecheVariante = ContextCompat.getColor(context, R.color.brand_surface_variant)

            binding.textDayNumber.text = zelle.datum.dayOfMonth.toString()
            binding.textDayNumber.setTypeface(null, if (zelle.istHeute) Typeface.BOLD else Typeface.NORMAL)
            binding.textDayNumber.setTextColor(
                if (zelle.istHeute) basisfarbe else ContextCompat.getColor(context, R.color.brand_on_surface),
            )

            when {
                zelle.echteIntensitaet != null -> {
                    // Mindest-Alpha, damit auch Stufe 1 noch sichtbar eingefärbt ist.
                    val alpha = (zelle.echteIntensitaet / 10f * 255).toInt().coerceIn(60, 255)
                    binding.cardDay.setCardBackgroundColor(ColorUtils.setAlphaComponent(basisfarbe, alpha))
                    binding.cardDay.strokeWidth = 0
                }
                zelle.prognoseIntensitaet != null -> {
                    binding.cardDay.setCardBackgroundColor(oberflaeche)
                    binding.cardDay.strokeColor = basisfarbe
                    binding.cardDay.strokeWidth = dp(1.5f)
                }
                else -> {
                    binding.cardDay.setCardBackgroundColor(oberflaecheVariante)
                    binding.cardDay.strokeWidth = 0
                }
            }

            binding.root.setOnClickListener { onDayClick(zelle.datum) }
        }
    }
}
