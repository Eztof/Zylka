package com.oliver.zylka.plants

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.Sensor
import com.oliver.zylka.data.plants.SensorRepository
import com.oliver.zylka.databinding.ActivitySensorsBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Liste aller Bluetooth-Sensoren (durchsuchbar). Tippen öffnet [SensorDetailActivity], "+"
 * öffnet [SensorEditActivity] zum Anlegen. */
class SensorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensorsBinding
    private val sensorRepository = SensorRepository()
    private val adapter = SensorAdapter { sensor -> startActivity(SensorDetailActivity.intent(this, sensor.id)) }

    private var allSensors: List<Sensor> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerSensors.layoutManager = LinearLayoutManager(this)
        binding.recyclerSensors.adapter = adapter

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                renderList()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensorRepository.observeSensors().collect { sensors ->
                    allSensors = sensors.sortedBy { it.name.lowercase() }
                    renderList()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sensors, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_sensor) {
            startActivity(Intent(this, SensorEditActivity::class.java))
            return true
        }
        if (item.itemId == R.id.action_ble_diagnostic) {
            startActivity(SensorDiagnosticActivity.intent(this))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun renderList() {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allSensors else allSensors.filter { it.name.lowercase().contains(q) }
        adapter.submitList(filtered)
        binding.textEmpty.isVisible = filtered.isEmpty()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SensorsActivity::class.java)
    }
}
