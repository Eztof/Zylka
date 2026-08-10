package com.oliver.zylka.hochzeit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.hochzeit.TerminOption
import com.oliver.zylka.data.hochzeit.TerminStatus
import com.oliver.zylka.databinding.ItemTerminBinding
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ein Termin-Kandidat innerhalb eines [com.oliver.zylka.data.hochzeit.TerminTyp]-Abschnitts
 * (siehe [TerminUebersichtActivity]) - Tippen öffnet denselben Editor-Dialog wie "+", nur
 * vorausgefüllt. */
class TerminAdapter(private val onClick: (TerminOption) -> Unit) : RecyclerView.Adapter<TerminAdapter.ViewHolder>() {

    private var termine: List<TerminOption> = emptyList()

    fun submitList(newTermine: List<TerminOption>) {
        termine = newTermine
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTerminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(termine[position])

    override fun getItemCount(): Int = termine.size

    class ViewHolder(
        private val binding: ItemTerminBinding,
        private val onClick: (TerminOption) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val datumFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)

        fun bind(termin: TerminOption) {
            val context = binding.root.context
            binding.textTerminDatum.text = datumFormat.format(termin.datum).replaceFirstChar { it.uppercase() }
            binding.textTerminNotiz.text = termin.notiz
            binding.textTerminNotiz.isVisible = termin.notiz.isNotBlank()

            binding.textTerminStatus.text = context.getString(termin.status.label)
            binding.textTerminStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    when (termin.status) {
                        TerminStatus.BESTAETIGT -> R.color.brand_primary
                        TerminStatus.VORGESCHLAGEN -> R.color.brand_secondary
                        TerminStatus.ABGESAGT -> R.color.brand_outline
                    },
                ),
            )

            binding.root.setOnClickListener { onClick(termin) }
        }
    }
}
