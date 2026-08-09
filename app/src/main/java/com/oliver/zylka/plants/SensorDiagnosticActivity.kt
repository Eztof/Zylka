package com.oliver.zylka.plants

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rohdaten-Diagnose: protokolliert **jedes** empfangene BLE-Werbepaket in der Nähe (nicht nur
 * den letzten Wert je Gerät) mit Zeitstempel, Name/MAC, Signalstärke und dem vollen Hex-Dump -
 * unabhängig davon, ob [SensorBleScanner] es als TP357 erkennt. Ein Filter grenzt das Log auf
 * ein einzelnes Gerät ein (z. B. Namen oder MAC-Ausschnitt eintippen), "Kopieren" legt einen
 * einzelnen Eintrag in die Zwischenablage, "Exportieren" teilt das komplette (gefilterte) Log
 * als Text - damit sich ein Sensor identifizieren und sein Byte-Format extern ableiten lässt.
 */
class SensorDiagnosticActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorDiagnosticBinding
    private val bleScanner by lazy { SensorBleScanner(applicationContext) }
    private val adapter = BleDiagnosticAdapter { entry -> copyToClipboard(entry) }
    private val exportTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    /** Neueste zuerst; auf [MAX_LOG_ENTRIES] begrenzt, damit ein länger offener Screen nicht
     * unbegrenzt Speicher aufbaut. */
    private val log = ArrayDeque<RawScanResult>()
    private var query: String = ""
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
        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                render()
            }
        })

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sensor_diagnostic, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_export_diagnostic) {
            exportLog()
            return true
        }
        return super.onOptionsItemSelected(item)
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
                        log.addFirst(result)
                        while (log.size > MAX_LOG_ENTRIES) log.removeLast()
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
        render()
    }

    private fun render() {
        binding.progressScanning.isVisible = false
        val filtered = filteredLog()
        adapter.submitList(filtered)
        binding.textEmpty.isVisible = filtered.isEmpty()
    }

    private fun filteredLog(): List<RawScanResult> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return log.toList()
        return log.filter {
            (it.name?.lowercase()?.contains(q) == true) || it.macAddress.lowercase().contains(q)
        }
    }

    private fun copyToClipboard(entry: RawScanResult) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(entry.macAddress, formatEntry(entry)))
        Toast.makeText(this, R.string.sensor_diagnostic_copied, Toast.LENGTH_SHORT).show()
    }

    private fun exportLog() {
        val filtered = filteredLog()
        if (filtered.isEmpty()) {
            Toast.makeText(this, R.string.sensor_diagnostic_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = filtered.joinToString("\n\n") { formatEntry(it) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.sensor_diagnostic_export_chooser_title)))
    }

    private fun formatEntry(entry: RawScanResult): String = buildString {
        appendLine(exportTimeFormat.format(Date(entry.epochMillis)))
        appendLine(entry.name ?: getString(R.string.sensor_unknown_device))
        appendLine(entry.macAddress)
        appendLine(getString(R.string.sensor_diagnostic_rssi, entry.rssi))
        appendLine(entry.rawBytesHex)
        entry.manufacturerData.forEach { appendLine(it) }
    }.trim()

    companion object {
        private const val MAX_LOG_ENTRIES = 500
        private const val RENDER_THROTTLE_MILLIS = 400L

        fun intent(context: Context): Intent = Intent(context, SensorDiagnosticActivity::class.java)
    }
}
