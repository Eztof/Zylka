package com.oliver.zylka.notenspiegel

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.databinding.ItemGradeThresholdEditBinding

/** Editable list of "Mindestprozent je Note" rows for the settings screen. Keeps its own
 * mutable copy of the values so typing survives RecyclerView recycling; [currentValues]
 * reads out the edited list for saving. */
class GradeThresholdEditAdapter : RecyclerView.Adapter<GradeThresholdEditAdapter.ViewHolder>() {

    private var grades: List<String> = emptyList()
    private var values: MutableList<Double> = mutableListOf()

    fun submit(grades: List<String>, values: List<Double>) {
        this.grades = grades
        this.values = values.toMutableList()
        notifyDataSetChanged()
    }

    fun currentValues(): List<Double> = values.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGradeThresholdEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(grades[position], values[position]) { newValue -> values[position] = newValue }
    }

    override fun getItemCount(): Int = grades.size

    class ViewHolder(private val binding: ItemGradeThresholdEditBinding) : RecyclerView.ViewHolder(binding.root) {
        private var watcher: TextWatcher? = null

        fun bind(grade: String, value: Double, onChanged: (Double) -> Unit) {
            binding.textGrade.text = grade

            watcher?.let { binding.inputPercent.removeTextChangedListener(it) }
            val text = GradeBandAdapter.formatPoints(value)
            if (binding.inputPercent.text?.toString() != text) {
                binding.inputPercent.setText(text)
            }
            val newWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val parsed = s?.toString()?.replace(',', '.')?.toDoubleOrNull()
                    if (parsed != null) onChanged(parsed)
                }
            }
            binding.inputPercent.addTextChangedListener(newWatcher)
            watcher = newWatcher
        }
    }
}
