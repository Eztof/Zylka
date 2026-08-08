package com.oliver.zylka.notenspiegel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.notenspiegel.GradeBand
import com.oliver.zylka.databinding.ItemGradeBandBinding
import java.util.Locale

/** Shows the computed point range per grade, best grade first. */
class GradeBandAdapter : RecyclerView.Adapter<GradeBandAdapter.ViewHolder>() {

    private var bands: List<GradeBand> = emptyList()

    fun submitList(newBands: List<GradeBand>) {
        bands = newBands
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGradeBandBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(bands[position])

    override fun getItemCount(): Int = bands.size

    class ViewHolder(private val binding: ItemGradeBandBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(band: GradeBand) {
            binding.textGrade.text = band.grade
            binding.textRange.text = binding.root.context.getString(
                R.string.notenspiegel_points_range,
                formatPoints(band.minPoints),
                formatPoints(band.maxPoints),
            )
        }
    }

    companion object {
        fun formatPoints(value: Double): String =
            if (value == Math.floor(value)) {
                value.toLong().toString()
            } else {
                String.format(Locale.GERMANY, "%.1f", value)
            }
    }
}
