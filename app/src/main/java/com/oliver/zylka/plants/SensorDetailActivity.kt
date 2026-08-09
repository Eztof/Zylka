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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.Sensor
import com.oliver.zylka.data.plants.SensorBleScanner
import com.oliver.zylka.data.plants.SensorReadingRepository
import com.oliver.zylka.data.plants.SensorRepository
import com.oliver.zylka.databinding.ActivitySensorDetailBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Aktueller Messwert + Verlauf eines Sensors, Button "Jetzt abrufen" pullt per Bluetooth
 * eine neue Messung (siehe [SensorBleScanner]). */
class SensorDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorDetailBinding
    private val authRepository = AuthRepository()
    private val sensorRepository = SensorRepository()
    private val sensorReadingRepository = SensorReadingRepository()
    private val bleScanner by lazy { SensorBleScanner(applicationContext) }
    private val historyAdapter = SensorReadingAdapter()
    private val timeFormat = SimpleDateFormat("d.M., HH:mm", Locale.GERMANY)

    private lateinit var sensorId: String
    private var currentSensor = Sensor()

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> if (results.values.all { it }) pullReading() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sensorId = intent.getStringExtra(EXTRA_SENSOR_ID) ?: run { finish(); return }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = historyAdapter
        binding.buttonPull.setOnClickListener { onPullClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorRepository.observeSensor(sensorId).collect { sensor ->
                    if (sensor != null) {
                        currentSensor = sensor
                        title = sensor.name
                        updateHeader(sensor)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorReadingRepository.observeReadings(sensorId).collect { readings ->
                    historyAdapter.submitList(readings)
                    binding.textHistoryEmpty.isVisible = readings.isEmpty()
                }
            }
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
        if (item.itemId == R.id.action_edit_sensor) {
            startActivity(SensorEditActivity.intent(this, sensorId))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateHeader(sensor: Sensor) {
        binding.textMac.text = sensor.macAddress
        val temperature = sensor.lastTemperatureC
        val humidity = sensor.lastHumidityPercent
        binding.textCurrentReading.text = if (temperature != null && humidity != null) {
            val zeit = sensor.lastMeasuredAt?.let { timeFormat.format(it) }.orEmpty()
            getString(R.string.sensor_reading_summary, temperature, humidity, zeit)
        } else {
            getString(R.string.sensor_no_reading)
        }
    }

    private fun onPullClicked() {
        if (bleScanner.hasPermissions()) {
            pullReading()
        } else {
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

    companion object {
        private const val EXTRA_SENSOR_ID = "sensor_id"

        fun intent(context: Context, sensorId: String): Intent =
            Intent(context, SensorDetailActivity::class.java).putExtra(EXTRA_SENSOR_ID, sensorId)
    }
}
