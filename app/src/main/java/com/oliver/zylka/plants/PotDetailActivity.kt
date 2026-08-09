package com.oliver.zylka.plants

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.PlantForecastRepository
import com.oliver.zylka.data.plants.Pot
import com.oliver.zylka.data.plants.PotRepository
import com.oliver.zylka.data.plants.WateringRepository
import com.oliver.zylka.databinding.ActivityPotDetailBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/**
 * Verlauf der Gießvorgänge eines Topfs plus die simulierte Vorratskurve über den von
 * Open-Meteo abgedeckten 14-Tage-Ausschnitt (7 Tage zurück, 7 Tage Prognose) als
 * [PotWaterLevelChartView].
 */
class PotDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPotDetailBinding
    private val potRepository = PotRepository()
    private val wateringRepository = WateringRepository()
    private val forecastRepository by lazy { PlantForecastRepository(applicationContext) }
    private val historyAdapter = WateringHistoryAdapter()

    private lateinit var potId: String
    private var currentPot: Pot = Pot()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPotDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        potId = intent.getStringExtra(EXTRA_POT_ID) ?: run { finish(); return }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = historyAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                potRepository.observePot(potId).collect { pot ->
                    if (pot != null) {
                        currentPot = pot
                        title = pot.name
                        loadChart()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                wateringRepository.observeWaterings(potId).collect { waterings ->
                    historyAdapter.submitList(waterings)
                    binding.textHistoryEmpty.isVisible = waterings.isEmpty()
                    // Ein neuer Gießvorgang verändert auch die Kurve (Reset auf Kapazität) -
                    // Prognose inkl. Kurve deshalb mit neu laden.
                    loadChart()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pot_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_edit_pot) {
            startActivity(PotEditActivity.intent(this, potId))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadChart() {
        lifecycleScope.launch {
            val waterings = wateringRepository.loadWaterings(potId)
            val forecasts = forecastRepository.computeForecasts(currentPot.uid)
            val forecast = forecasts.firstOrNull { it.pot.id == potId }
            binding.textNoWeatherHint.isVisible = forecast == null || !forecast.hasLocation

            if (forecast != null && forecast.hasLocation) {
                binding.textStatusSummary.text = getString(
                    R.string.pot_detail_status_summary,
                    forecast.percentFull,
                )
            } else {
                binding.textStatusSummary.text = getString(R.string.plants_status_no_location)
            }

            binding.chartWaterLevel.setData(
                points = forecast?.verlauf.orEmpty(),
                kapazitaetMm = currentPot.kapazitaetMm,
                nowEpochMillis = System.currentTimeMillis(),
                wateringTimestamps = waterings.mapNotNull { it.wateredAt?.time },
            )
        }
    }

    companion object {
        private const val EXTRA_POT_ID = "pot_id"

        fun intent(context: Context, potId: String): Intent =
            Intent(context, PotDetailActivity::class.java).putExtra(EXTRA_POT_ID, potId)
    }
}
