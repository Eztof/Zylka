package com.oliver.zylka.hochzeit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.hochzeit.Dienstleister
import com.oliver.zylka.data.hochzeit.DienstleisterStatus
import com.oliver.zylka.databinding.ItemDienstleisterBinding
import java.util.Locale

/** Ein Dienstleister/Kostenposten in der Liste (siehe [DienstleisterListeActivity]) - Tippen
 * öffnet [DienstleisterEditActivity]. Die Statusfarbe bildet die Buchungs-Pipeline ab: neutral
 * (Idee) -> amber (angefragt) -> blau (Angebot da) -> grün (gebucht), Absage bleibt neutral statt
 * rot - eine Absage ist kein Fehler. */
class DienstleisterAdapter(
    private val onClick: (Dienstleister) -> Unit,
) : RecyclerView.Adapter<DienstleisterAdapter.ViewHolder>() {

    private var dienstleister: List<Dienstleister> = emptyList()

    fun submitList(newDienstleister: List<Dienstleister>) {
        dienstleister = newDienstleister
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDienstleisterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(dienstleister[position])

    override fun getItemCount(): Int = dienstleister.size

    class ViewHolder(
        private val binding: ItemDienstleisterBinding,
        private val onClick: (Dienstleister) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(eintrag: Dienstleister) {
            val context = binding.root.context
            binding.textDienstleisterName.text = eintrag.name
            binding.textDienstleisterKategorie.text = context.getString(eintrag.kategorie.label)

            binding.textDienstleisterStatus.text = context.getString(eintrag.status.label)
            binding.textDienstleisterStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    when (eintrag.status) {
                        DienstleisterStatus.GEBUCHT -> R.color.brand_primary
                        DienstleisterStatus.ANGEBOT_ERHALTEN -> R.color.brand_tertiary
                        DienstleisterStatus.ANGEFRAGT -> R.color.brand_secondary
                        DienstleisterStatus.IDEE, DienstleisterStatus.ABGESAGT -> R.color.brand_outline
                    },
                ),
            )

            val kosten = eintrag.kostenEuro
            binding.textDienstleisterKosten.isVisible = kosten != null
            if (kosten != null) {
                binding.textDienstleisterKosten.text = String.format(Locale.GERMANY, "%.2f €", kosten)
            }

            binding.root.setOnClickListener { onClick(eintrag) }
        }
    }
}
