package com.oliver.zylka.hochzeit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.hochzeit.Gast
import com.oliver.zylka.data.hochzeit.GastPrioritaet
import com.oliver.zylka.databinding.ItemGastBinding

/** Ein Gast in der Liste (siehe [GastListeActivity]) - Tippen öffnet [GastEditActivity]. */
class GastAdapter(private val onClick: (Gast) -> Unit) : RecyclerView.Adapter<GastAdapter.ViewHolder>() {

    private var gaeste: List<Gast> = emptyList()

    fun submitList(newGaeste: List<Gast>) {
        gaeste = newGaeste
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(gaeste[position])

    override fun getItemCount(): Int = gaeste.size

    class ViewHolder(
        private val binding: ItemGastBinding,
        private val onClick: (Gast) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gast: Gast) {
            val context = binding.root.context
            binding.textGastName.text = gast.name
            binding.textGastKategorie.text = context.getString(gast.kategorie.label)

            binding.textGastPrioritaet.text = context.getString(gast.prioritaet.label)
            binding.textGastPrioritaet.setTextColor(
                ContextCompat.getColor(
                    context,
                    when (gast.prioritaet) {
                        GastPrioritaet.SICHER -> R.color.brand_primary
                        GastPrioritaet.MITTEL -> R.color.brand_secondary
                        GastPrioritaet.MAEH -> R.color.brand_outline
                    },
                ),
            )

            binding.root.setOnClickListener { onClick(gast) }
        }
    }
}
