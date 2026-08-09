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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.RawScanResult
import com.oliver.zylka.data.plants.SensorBleScanner
import com.oliver.zylka.data.plants.SensorRepository
import com.oliver.zylka.databinding.ActivitySensorDiagnosticBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rohdaten-Diagnose: protokolliert **jedes** empfangene BLE-Paket mit Zeitstempel,
 * Name/MAC, Signalstärke und dem vollen Hex-Dump.
 *
 * Zwei Einstiege:
 * - **Fokussiert** (von [SensorDetailActivity] über [intent] mit `macAddress`): verbindet sich
 *   sofort per GATT mit genau diesem Sensor und schickt die Historien-Anfrage - kein Scannen,
 *   kein Auswählen, keine anderen Geräte in Liste oder Export.
 * - **Allgemein** (von `SensorsActivity`, ohne `macAddress`): startet im passiven
 *   Advertisement-Scan, gefiltert auf bereits angelegte Sensoren (siehe [knownMacs]) - fremde
 *   BLE-Geräte in der Nähe (Kopfhörer, andere Sensoren o. Ä.) tauchen so gar nicht erst in der
 *   Liste auf. Über das Menü lässt sich von dort trotzdem manuell mit einem der gefundenen
 *   Sensoren per GATT verbinden oder eine Verlaufsanfrage testen.
 *
 * Ein Textfilter grenzt zusätzlich weiter ein, "Kopieren" legt einen einzelnen Eintrag in die
 * Zwischenablage, "Exportieren" teilt das komplette (gefilterte) Log als Text.
 */
class SensorDiagnosticActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorDiagnosticBinding
    private val bleScanner by lazy { SensorBleScanner(applicationContext) }
    private val sensorRepository = SensorRepository()
    private val adapter = BleDiagnosticAdapter { entry -> copyToClipboard(entry) }
    private val exportTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    /** Neueste zuerst; auf [MAX_LOG_ENTRIES] begrenzt, damit ein länger offener Screen nicht
     * unbegrenzt Speicher aufbaut. */
    private val log = ArrayDeque<RawScanResult>()
    private var query: String = ""
    private var lastRenderAt = 0L
    private var scanJob: Job? = null

    /** null = passiver Advertisement-Scan; gesetzt = per GATT mit dieser MAC verbunden. */
    private var gattMac: String? = null

    /** Nur relevant, wenn [gattMac] gesetzt ist: ob beim Verbinden zusätzlich die
     * Historien-Anfrage-Sequenz aus [SensorBleScanner.readHistory] geschickt wird (roh
     * protokolliert, unabhängig vom erwarteten Antwortformat). */
    private var sendHistoryRequestOnConnect = false

    /** Gesetzt, wenn über [intent] mit einer festen MAC-Adresse gestartet - dann bleibt der
     * Screen dauerhaft auf dieses eine Gerät fokussiert (kein "Zurück zum Scan"). */
    private var focusedMode = false

    /** MAC-Adressen aller bereits angelegten Sensoren (Großschreibung) - im allgemeinen
     * Scan-Modus wird die Liste/der Export darauf beschränkt, damit fremde BLE-Geräte in der
     * Nähe nicht mit auftauchen. Leer, solange noch nicht geladen. */
    private var knownMacs: Set<String> = emptySet()

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> if (results.values.all { it }) startInitialMode() else finish() }

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

        val presetMac = intent.getStringExtra(EXTRA_MAC_ADDRESS)
        if (presetMac != null) {
            focusedMode = true
            gattMac = presetMac
            sendHistoryRequestOnConnect = intent.getBooleanExtra(EXTRA_AUTO_HISTORY, true)
            val presetName = intent.getStringExtra(EXTRA_NAME)
            binding.layoutSearch.isVisible = false
            binding.textCaption.text = getString(
                if (sendHistoryRequestOnConnect) R.string.sensor_diagnostic_gatt_history_active else R.string.sensor_diagnostic_gatt_active,
                presetName ?: presetMac,
            )
        } else {
            lifecycleScope.launch {
                knownMacs = sensorRepository.loadSensors().map { it.macAddress.uppercase() }.toSet()
                render()
            }
        }

        if (bleScanner.hasPermissions()) {
            startInitialMode()
        } else {
            requestBlePermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun startInitialMode() {
        startScanning()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sensor_diagnostic, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val connected = gattMac != null
        if (focusedMode) {
            // Fokussiert auf ein Gerät (von SensorDetailActivity) - kein Moduswechsel nötig,
            // nur "Exportieren" bleibt übrig.
            menu.findItem(R.id.action_gatt_connect)?.isVisible = false
            menu.findItem(R.id.action_gatt_history_request)?.isVisible = false
        } else {
            menu.findItem(R.id.action_gatt_connect)?.title = getString(
                if (connected) R.string.sensor_diagnostic_action_gatt_stop else R.string.sensor_diagnostic_action_gatt_start,
            )
            menu.findItem(R.id.action_gatt_history_request)?.isVisible = !connected
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_export_diagnostic -> exportLog()
            R.id.action_gatt_connect -> onGattConnectClicked()
            R.id.action_gatt_history_request -> onGattHistoryRequestClicked()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onGattConnectClicked() {
        if (gattMac != null) {
            disconnectGattMode()
            return
        }
        pickGattTargetDevice { mac ->
            sendHistoryRequestOnConnect = false
            connectGattMode(mac)
        }
    }

    private fun onGattHistoryRequestClicked() {
        if (gattMac != null) {
            disconnectGattMode()
            return
        }
        pickGattTargetDevice { mac ->
            sendHistoryRequestOnConnect = true
            connectGattMode(mac)
        }
    }

    /** Nur Geräte, die schon in der (bereits gefilterten) Liste sichtbar sind - fremde
     * BLE-Geräte landen so gar nicht erst in dieser Auswahl. */
    private fun pickGattTargetDevice(onPicked: (String) -> Unit) {
        val visible = filteredLog()
        val macs = visible.map { it.macAddress }.distinct()
        if (macs.isEmpty()) {
            Toast.makeText(this, R.string.sensor_diagnostic_gatt_no_devices, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = macs.map { mac -> visible.first { it.macAddress == mac }.name?.let { "$it ($mac)" } ?: mac }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.sensor_diagnostic_gatt_pick_title)
            .setItems(labels) { _, which -> onPicked(macs[which]) }
            .show()
    }

    private fun connectGattMode(mac: String) {
        gattMac = mac
        binding.textCaption.text = getString(
            if (sendHistoryRequestOnConnect) R.string.sensor_diagnostic_gatt_history_active else R.string.sensor_diagnostic_gatt_active,
            mac,
        )
        invalidateOptionsMenu()
        startScanning()
    }

    private fun disconnectGattMode() {
        gattMac = null
        sendHistoryRequestOnConnect = false
        binding.textCaption.setText(R.string.sensor_diagnostic_caption)
        invalidateOptionsMenu()
        startScanning()
    }

    private fun startScanning() {
        if (!bleScanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.sensor_bluetooth_disabled, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.progressScanning.isVisible = true
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val flow = gattMac?.let { bleScanner.observeGattFlow(it, sendHistoryRequestOnConnect) } ?: bleScanner.scanRawFlow()
                    flow.collect { result ->
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

    /**
     * Im GATT-/Verlaufs-Modus enthält [log] ohnehin nur Traffic des einen verbundenen Geräts,
     * eine Filterung ist dort nicht nötig. Im passiven Scan-Modus dagegen kommen alle BLE-Geräte
     * in der Nähe rein - dort wird auf bereits angelegte Sensoren ([knownMacs]) eingegrenzt,
     * damit fremde Geräte (Kopfhörer, Reifensensoren, ...) nicht in Liste oder Export landen.
     */
    private fun filteredLog(): List<RawScanResult> {
        val base = if (gattMac != null || knownMacs.isEmpty()) {
            log
        } else {
            log.filter { it.macAddress.uppercase() in knownMacs }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return base.toList()
        return base.filter {
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
        if (entry.rssi != null) appendLine(getString(R.string.sensor_diagnostic_rssi, entry.rssi))
        appendLine(entry.rawBytesHex)
        entry.extraLines.forEach { appendLine(it) }
    }.trim()

    companion object {
        private const val MAX_LOG_ENTRIES = 500
        private const val RENDER_THROTTLE_MILLIS = 400L
        private const val EXTRA_MAC_ADDRESS = "mac_address"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_AUTO_HISTORY = "auto_history"

        /** Allgemeiner Einstieg: Scan-Modus, gefiltert auf bereits angelegte Sensoren. */
        fun intent(context: Context): Intent = Intent(context, SensorDiagnosticActivity::class.java)

        /** Fokussierter Einstieg für einen bestimmten Sensor (z. B. von [SensorDetailActivity]):
         * verbindet sich sofort per GATT, kein Scannen/Auswählen, keine anderen Geräte sichtbar. */
        fun intent(context: Context, macAddress: String, name: String, autoStartHistoryRequest: Boolean = true): Intent =
            Intent(context, SensorDiagnosticActivity::class.java)
                .putExtra(EXTRA_MAC_ADDRESS, macAddress)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_AUTO_HISTORY, autoStartHistoryRequest)
    }
}
