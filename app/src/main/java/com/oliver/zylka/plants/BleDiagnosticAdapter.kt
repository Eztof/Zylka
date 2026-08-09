package com.oliver.zylka.plants

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.RawScanResult
import com.oliver.zylka.databinding.ItemBleDiagnosticBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log aller empfangenen BLE-Rohpakete (neueste zuerst, ungefiltert bis auf die Textsuche in
 * [SensorDiagnosticActivity]) - jedes Paket einzeln, nicht nur der letzte Wert je Gerät, damit
 * sich Veränderungen zwischen aufeinanderfolgenden Paketen ablesen lassen. */
class BleDiagnosticAdapter(
    private val onCopy: (RawScanResult) -> Unit,
) : RecyclerView.Adapter<BleDiagnosticAdapter.ViewHolder>() {

    private var entries: List<RawScanResult> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    fun submitList(newEntries: List<RawScanResult>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBleDiagnosticBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onCopy, timeFormat)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(entries[position])

    override fun getItemCount(): Int = entries.size

    class ViewHolder(
        private val binding: ItemBleDiagnosticBinding,
        private val onCopy: (RawScanResult) -> Unit,
        private val timeFormat: SimpleDateFormat,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RawScanResult) {
            val context = binding.root.context
            binding.textDeviceName.text = entry.name ?: context.getString(R.string.sensor_unknown_device)
            binding.textDeviceRssi.text = context.getString(
                R.string.sensor_diagnostic_meta,
                timeFormat.format(Date(entry.epochMillis)),
                entry.rssi,
            )
            binding.textDeviceMac.text = entry.macAddress
            binding.textRawBytes.text = entry.rawBytesHex
            if (entry.manufacturerData.isEmpty()) {
                binding.textManufacturerData.isVisible = false
            } else {
                binding.textManufacturerData.isVisible = true
                binding.textManufacturerData.text = entry.manufacturerData.joinToString("\n")
            }
            binding.buttonCopy.setOnClickListener { onCopy(entry) }
        }
    }
}
