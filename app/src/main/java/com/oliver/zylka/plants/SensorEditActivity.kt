package com.oliver.zylka.plants

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.DiscoveredDevice
import com.oliver.zylka.data.plants.Sensor
import com.oliver.zylka.data.plants.SensorBleScanner
import com.oliver.zylka.data.plants.SensorRepository
import com.oliver.zylka.databinding.ActivitySensorEditBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Sensor anlegen/bearbeiten: Name, MAC-Adresse (per Hand oder über "In der Nähe suchen" aus
 * einer BLE-Geräte-Liste übernommen). */
class SensorEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorEditBinding
    private val authRepository = AuthRepository()
    private val sensorRepository = SensorRepository()
    private val bleScanner by lazy { SensorBleScanner(applicationContext) }

    private var sensor = Sensor()

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) scanForDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonScan.setOnClickListener { onScanClicked() }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        val sensorId = intent.getStringExtra(EXTRA_SENSOR_ID)
        if (sensorId == null) {
            title = getString(R.string.sensor_edit_title_new)
        } else {
            binding.buttonDelete.isVisible = true
            lifecycleScope.launch {
                val loaded = sensorRepository.getSensor(sensorId) ?: Sensor(id = sensorId)
                sensor = loaded
                title = getString(R.string.sensor_edit_title_edit)
                binding.inputName.setText(loaded.name)
                binding.inputMac.setText(loaded.macAddress)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun onScanClicked() {
        if (bleScanner.hasPermissions()) {
            scanForDevices()
        } else {
            requestBlePermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun scanForDevices() {
        if (!bleScanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.sensor_bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressScan.isVisible = true
        lifecycleScope.launch {
            val devices = bleScanner.discoverNearbyDevices()
            binding.progressScan.isVisible = false
            if (devices.isEmpty()) {
                Toast.makeText(this@SensorEditActivity, R.string.sensor_scan_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            showDevicePicker(devices)
        }
    }

    private fun showDevicePicker(devices: List<DiscoveredDevice>) {
        val labels = devices.map {
            "${it.name ?: getString(R.string.sensor_unknown_device)} (${it.macAddress})"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.sensor_scan_title)
            .setItems(labels) { _, which ->
                val device = devices[which]
                binding.inputMac.setText(device.macAddress)
                if (binding.inputName.text.isNullOrBlank() && device.name != null) {
                    binding.inputName.setText(device.name)
                }
            }
            .show()
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val mac = binding.inputMac.text?.toString()?.trim().orEmpty()
        if (name.isBlank() || mac.isBlank()) {
            Toast.makeText(this, R.string.sensor_edit_error_required, Toast.LENGTH_SHORT).show()
            return
        }
        val uid = authRepository.currentUser?.uid ?: return
        lifecycleScope.launch {
            sensorRepository.save(sensor.copy(uid = uid, name = name, macAddress = mac))
            finish()
        }
    }

    private fun confirmDelete() {
        if (sensor.id.isBlank()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.sensor_edit_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    sensorRepository.delete(sensor.id)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_SENSOR_ID = "sensor_id"

        fun intent(context: Context, sensorId: String? = null): Intent =
            Intent(context, SensorEditActivity::class.java).apply {
                if (sensorId != null) putExtra(EXTRA_SENSOR_ID, sensorId)
            }
    }
}
