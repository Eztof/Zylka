package com.oliver.zylka.plants

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.HeatIndexCalculator
import com.oliver.zylka.data.plants.HistoricalPoint
import com.oliver.zylka.data.plants.Sensor
import com.oliver.zylka.data.plants.SensorBleScanner
import com.oliver.zylka.data.plants.SensorLiveCache
import com.oliver.zylka.data.plants.SensorReading
import com.oliver.zylka.data.plants.SensorReadingRepository
import com.oliver.zylka.data.plants.SensorRepository
import com.oliver.zylka.databinding.ActivitySensorDetailBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Aktueller Messwert + Verlauf eines Sensors. Lauscht dauerhaft (solange sichtbar) per BLE auf
 * Live-Werte (siehe [SensorLiveCache]) - "Jetzt abrufen" bleibt als gezielter Einzel-Pull
 * daneben bestehen (z. B. um sofort einen ersten Wert zu erzwingen). "Verlauf laden" importiert
 * die im Gerät gespeicherte Historie auf einmal (siehe [SensorBleScanner]). Kopf- und
 * Chart-Layout sind an die ThermoPro-App angelehnt (Komfort-Gauge, Temperatur-/Feuchte-Kurve),
 * hier in unserem Farbschema - siehe [ComfortGaugeView]/[SensorHistoryChartView]. */
class SensorDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorDetailBinding
    private val authRepository = AuthRepository()
    private val sensorRepository = SensorRepository()
    private val sensorReadingRepository = SensorReadingRepository()
    private val bleScanner by lazy { SensorBleScanner(applicationContext) }
    private val timeFormat = SimpleDateFormat("d.M., HH:mm", Locale.GERMANY)

    private lateinit var sensorId: String
    private var currentSensor = Sensor()
    private var pendingBleAction: (() -> Unit)? = null
    private var liveListenerStarted = false
    private var allReadings: List<SensorReading> = emptyList()
    private var selectedRange = TimeRange.DAY

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val action = pendingBleAction
        pendingBleAction = null
        if (results.values.all { it }) action?.invoke()
    }

    /** Zeiträume für die Verlaufs-Charts, an die ThermoPro-App angelehnt ("Hour/Day/Week/Month/
     * Year") - filtert einfach [allReadings] auf die letzten [rangeMillis] Millisekunden. */
    private enum class TimeRange(val rangeMillis: Long) {
        HOUR(60 * 60 * 1000L),
        DAY(24 * 60 * 60 * 1000L),
        WEEK(7L * 24 * 60 * 60 * 1000L),
        MONTH(30L * 24 * 60 * 60 * 1000L),
        YEAR(365L * 24 * 60 * 60 * 1000L),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sensorId = intent.getStringExtra(EXTRA_SENSOR_ID) ?: run { finish(); return }
        binding.buttonPull.setOnClickListener { ensureBlePermissions { pullReading() } }
        binding.buttonLoadHistory.setOnClickListener { ensureBlePermissions { pullHistory() } }
        binding.chartTemperature.setColors(
            lineColor = ContextCompat.getColor(this, R.color.brand_error),
            fillColor = ContextCompat.getColor(this, R.color.brand_error_container),
            markerColor = ContextCompat.getColor(this, R.color.brand_error),
            axisLabelColor = ContextCompat.getColor(this, R.color.brand_outline),
        )
        binding.chartHumidity.setColors(
            lineColor = ContextCompat.getColor(this, R.color.brand_tertiary),
            fillColor = ContextCompat.getColor(this, R.color.brand_tertiary_container),
            markerColor = ContextCompat.getColor(this, R.color.brand_tertiary),
            axisLabelColor = ContextCompat.getColor(this, R.color.brand_outline),
        )
        binding.toggleRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedRange = rangeFor(checkedId)
                renderCharts()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sensorRepository.observeSensor(sensorId).collect { sensor ->
                        if (sensor != null) {
                            currentSensor = sensor
                            title = sensor.name
                            updateHeader(sensor)
                            if (!liveListenerStarted && sensor.macAddress.isNotBlank()) {
                                liveListenerStarted = true
                                launch { listenForLiveReadings(sensor.macAddress) }
                            }
                        }
                    }
                }
                launch {
                    sensorReadingRepository.observeReadings(sensorId).collect { readings ->
                        allReadings = readings
                        renderCharts()
                    }
                }
            }
        }
    }

    private fun rangeFor(checkedId: Int): TimeRange = when (checkedId) {
        R.id.button_range_hour -> TimeRange.HOUR
        R.id.button_range_week -> TimeRange.WEEK
        R.id.button_range_month -> TimeRange.MONTH
        R.id.button_range_year -> TimeRange.YEAR
        else -> TimeRange.DAY
    }

    /** Füllt beide Charts mit den auf [selectedRange] gefilterten Messwerten - mit weniger als
     * zwei Punkten zeichnet [SensorHistoryChartView] nichts, dann zeigen wir stattdessen den
     * Leer-Hinweis. */
    private fun renderCharts() {
        val cutoffMillis = System.currentTimeMillis() - selectedRange.rangeMillis
        val windowed = allReadings
            .filter { (it.measuredAt?.time ?: 0L) >= cutoffMillis }
            .sortedBy { it.measuredAt?.time ?: 0L }

        val temperaturePoints = windowed.map { (it.measuredAt?.time ?: 0L) to it.temperatureC }
        binding.chartTemperature.setData(temperaturePoints, " °C")
        val hasTemperature = temperaturePoints.size >= 2
        binding.textTemperatureChartEmpty.isVisible = !hasTemperature
        binding.textTemperatureRange.isVisible = hasTemperature
        if (hasTemperature) {
            binding.textTemperatureRange.text = getString(
                R.string.sensor_chart_range_temperature,
                temperaturePoints.maxOf { it.second },
                temperaturePoints.minOf { it.second },
            )
        }

        val humidityPoints = windowed.map { (it.measuredAt?.time ?: 0L) to it.humidityPercent }
        binding.chartHumidity.setData(humidityPoints, " %")
        val hasHumidity = humidityPoints.size >= 2
        binding.textHumidityChartEmpty.isVisible = !hasHumidity
        binding.textHumidityRange.isVisible = hasHumidity
        if (hasHumidity) {
            binding.textHumidityRange.text = getString(
                R.string.sensor_chart_range_humidity,
                humidityPoints.maxOf { it.second },
                humidityPoints.minOf { it.second },
            )
        }
    }

    /** Bleibt per BLE mit dem Sensor verbunden, solange der Screen sichtbar ist (endet
     * automatisch mit dem umgebenden [repeatOnLifecycle]-Block), und aktualisiert den Kopf bei
     * jedem empfangenen Live-Wert sofort - ohne für jede Anzeige extra "Jetzt abrufen" drücken zu
     * müssen. Ein Verbindungsfehler wird verschluckt; beim nächsten Sichtbarwerden des Screens
     * versucht es [liveListenerStarted]-gesteuert automatisch erneut. */
    private suspend fun listenForLiveReadings(macAddress: String) {
        try {
            bleScanner.observeLiveReadings(macAddress).collect { reading ->
                SensorLiveCache.update(macAddress, reading)
                updateHeader(currentSensor)
            }
        } catch (error: Exception) {
            liveListenerStarted = false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sensor_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit_sensor -> {
                startActivity(SensorEditActivity.intent(this, sensorId))
                return true
            }
            R.id.action_diagnose_sensor -> {
                val mac = currentSensor.macAddress
                if (mac.isBlank()) return true
                startActivity(SensorDiagnosticActivity.intent(this, mac, currentSensor.name))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** Zeigt bevorzugt den per BLE dauerhaft gelauschten Live-Wert ([SensorLiveCache]) - erst
     * wenn (noch) keiner da ist, den zuletzt in Firestore gespeicherten. Wird sowohl beim Laden
     * des Sensors als auch bei jedem neuen Live-Wert erneut aufgerufen. */
    private fun updateHeader(sensor: Sensor) {
        binding.textMac.text = sensor.macAddress
        val live = SensorLiveCache.get(sensor.macAddress)
        val temperature = live?.temperatureC ?: sensor.lastTemperatureC
        val humidity = live?.humidityPercent ?: sensor.lastHumidityPercent

        binding.textStatus.text = when {
            live != null -> getString(R.string.sensor_status_live)
            sensor.lastMeasuredAt != null -> timeFormat.format(sensor.lastMeasuredAt)
            else -> getString(R.string.sensor_no_reading)
        }

        if (temperature != null && humidity != null) {
            binding.textTemperature.text = getString(R.string.sensor_value_temperature, temperature)
            binding.textHeatIndex.text = getString(
                R.string.sensor_value_temperature,
                HeatIndexCalculator.heatIndexCelsius(temperature, humidity),
            )
            binding.textHumidity.text = getString(R.string.sensor_value_humidity, humidity)
            binding.gaugeHumidity.setHumidity(humidity)
        } else {
            binding.textTemperature.text = getString(R.string.sensor_value_placeholder)
            binding.textHeatIndex.text = getString(R.string.sensor_value_placeholder)
            binding.textHumidity.text = getString(R.string.sensor_value_placeholder)
            binding.gaugeHumidity.setHumidity(null)
        }
    }

    private fun ensureBlePermissions(action: () -> Unit) {
        if (bleScanner.hasPermissions()) {
            action()
        } else {
            pendingBleAction = action
            requestBlePermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun pullReading() {
        if (!bleScanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.sensor_bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        val uid = authRepository.currentUser?.uid ?: return
        val mac = currentSensor.macAddress
        if (mac.isBlank()) return

        binding.progressPull.isVisible = true
        lifecycleScope.launch {
            val reading = bleScanner.readSensor(mac)
            binding.progressPull.isVisible = false
            if (reading == null) {
                Toast.makeText(this@SensorDetailActivity, R.string.sensor_pull_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            sensorReadingRepository.recordReading(uid, sensorId, reading.temperatureC, reading.humidityPercent)
            sensorRepository.updateLastReading(sensorId, reading.temperatureC, reading.humidityPercent)
            Toast.makeText(this@SensorDetailActivity, R.string.sensor_pull_success, Toast.LENGTH_SHORT).show()
        }
    }

    /** Lädt die gespeicherte Gerätehistorie und legt sie mit geschätzten Zeitstempeln an
     * (`jetzt - Index × Aufnahme-Intervall`, neuester Datensatz zuerst - siehe
     * [SensorBleScanner.readHistory]). Mehrfaches Drücken legt die Werte erneut an, da die
     * geschätzten Zeitstempel keine Duplikatprüfung erlauben. */
    private fun pullHistory() {
        if (!bleScanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.sensor_bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        val uid = authRepository.currentUser?.uid ?: return
        val mac = currentSensor.macAddress
        if (mac.isBlank()) return

        binding.progressHistory.isVisible = true
        lifecycleScope.launch {
            val history = bleScanner.readHistory(mac)
            binding.progressHistory.isVisible = false
            if (history == null) {
                // Keine (vollständige) Antwort: Timeout, Verbindungsabbruch oder fehlende
                // Characteristics - siehe SensorBleScanner.readHistory.
                Toast.makeText(this@SensorDetailActivity, R.string.sensor_history_pull_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (history.isEmpty()) {
                // Antwort kam vollständig an, aber kein einziger Datensatz war plausibel -
                // anders als "keine Antwort", siehe SensorBleScanner.HistoryAssembly.
                Toast.makeText(this@SensorDetailActivity, R.string.sensor_history_pull_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intervalMillis = SensorBleScanner.HISTORY_RECORD_INTERVAL_MINUTES * 60_000L
            val now = System.currentTimeMillis()
            val points = history.map { reading ->
                HistoricalPoint(
                    measuredAt = Date(now - reading.index * intervalMillis),
                    temperatureC = reading.temperatureC,
                    humidityPercent = reading.humidityPercent,
                )
            }
            sensorReadingRepository.recordHistoricalReadings(uid, sensorId, points)
            Toast.makeText(
                this@SensorDetailActivity,
                getString(R.string.sensor_history_pull_success, points.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    companion object {
        private const val EXTRA_SENSOR_ID = "sensor_id"

        fun intent(context: Context, sensorId: String): Intent =
            Intent(context, SensorDetailActivity::class.java).putExtra(EXTRA_SENSOR_ID, sensorId)
    }
}
