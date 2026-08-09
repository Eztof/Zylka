package com.oliver.zylka.plants

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.RawScanResult
import com.oliver.zylka.databinding.ItemBleDiagnosticBinding

/** Alle BLE-Geräte in der Nähe (roh, ungefiltert), sortiert nach Signalstärke - siehe
 * [SensorDiagnosticActivity]. */
class BleDiagnosticAdapter(
    private val onCopy: (RawScanResult) -> Unit,
) : RecyclerView.Adapter<BleDiagnosticAdapter.ViewHolder>() {

    private var devices: List<RawScanResult> = emptyList()

    fun submitList(newDevices: List<RawScanResult>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBleDiagnosticBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onCopy)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(devices[position])

    override fun getItemCount(): Int = devices.size

    class ViewHolder(
        private val binding: ItemBleDiagnosticBinding,
        private val onCopy: (RawScanResult) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: RawScanResult) {
            val context = binding.root.context
            binding.textDeviceName.text = device.name ?: context.getString(R.string.sensor_unknown_device)
            binding.textDeviceRssi.text = context.getString(R.string.sensor_diagnostic_rssi, device.rssi)
            binding.textDeviceMac.text = device.macAddress
            binding.textRawBytes.text = device.rawBytesHex
            if (device.manufacturerData.isEmpty()) {
                binding.textManufacturerData.isVisible = false
            } else {
                binding.textManufacturerData.isVisible = true
                binding.textManufacturerData.text = device.manufacturerData.joinToString("\n")
            }
            binding.buttonCopy.setOnClickListener { onCopy(device) }
        }
    }
}
