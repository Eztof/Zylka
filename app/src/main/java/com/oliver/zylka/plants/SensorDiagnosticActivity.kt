package com.oliver.zylka.plants

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.RawScanResult
import com.oliver.zylka.data.plants.SensorBleScanner
import com.oliver.zylka.databinding.ActivitySensorDiagnosticBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/**
 * Rohdaten-Diagnose: zeigt **alle** BLE-Geräte in der Nähe live mit Name/MAC/Signalstärke und
 * dem vollen Hex-Dump ihrer Werbedaten - unabhängig davon, ob [SensorBleScanner] sie als TP357
 * erkennt. Damit lässt sich ein Sensor per Nähe/RSSI identifizieren und sein tatsächliches
 * Byte-Format ablesen, falls das eingebaute (unverifizierte) Parsing daneben liegt - "Kopieren"
 * legt Name/MAC/Rohdaten eines Eintrags in die Zwischenablage.
 */
class SensorDiagnosticActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorDiagnosticBinding
    private val bleScanner by lazy { SensorBleScanner(applicationContext) }
    private val adapter = BleDiagnosticAdapter { device -> copyToClipboard(device) }

    private val devices = LinkedHashMap<String, RawScanResult>()
    private var lastRenderAt = 0L

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> if (results.values.all { it }) startScanning() else finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorDiagnosticBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        if (bleScanner.hasPermissions()) {
            startScanning()
        } else {
            requestBlePermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun startScanning() {
        if (!bleScanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.sensor_bluetooth_disabled, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.progressScanning.isVisible = true
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    bleScanner.scanRawFlow().collect { result ->
                        devices[result.macAddress] = result
                        renderThrottled()
                    }
                } catch (error: Exception) {
                    Toast.makeText(
                        this@SensorDiagnosticActivity,
                        error.message ?: getString(R.string.sensor_pull_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /** Werbepakete können mehrfach pro Sekunde eintreffen - die Liste höchstens alle 400 ms neu
     * zeichnen, statt bei jedem einzelnen Paket. */
    private fun renderThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastRenderAt < RENDER_THROTTLE_MILLIS) return
        lastRenderAt = now
        binding.progressScanning.isVisible = false
        val sorted = devices.values.sortedByDescending { it.rssi }
        adapter.submitList(sorted)
        binding.textEmpty.isVisible = sorted.isEmpty()
    }

    private fun copyToClipboard(device: RawScanResult) {
        val text = buildString {
            appendLine(device.name ?: getString(R.string.sensor_unknown_device))
            appendLine(device.macAddress)
            appendLine(getString(R.string.sensor_diagnostic_rssi, device.rssi))
            appendLine(device.rawBytesHex)
            device.manufacturerData.forEach { appendLine(it) }
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(device.macAddress, text.trim()))
        Toast.makeText(this, R.string.sensor_diagnostic_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val RENDER_THROTTLE_MILLIS = 400L

        fun intent(context: Context): Intent = Intent(context, SensorDiagnosticActivity::class.java)
    }
}
