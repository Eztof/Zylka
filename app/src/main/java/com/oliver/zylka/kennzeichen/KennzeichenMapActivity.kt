package com.oliver.zylka.kennzeichen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.CatalogRepository
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.DiscoveryRepository
import com.oliver.zylka.data.kennzeichen.GeoRepository
import com.oliver.zylka.data.kennzeichen.GeoShape
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ActivityKennzeichenMapBinding
import kotlinx.coroutines.launch

/** Real geographic map of a country's regions, shaded by what's been discovered. */
class KennzeichenMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKennzeichenMapBinding
    private lateinit var country: Country
    private val authRepository = AuthRepository()
    private val catalogRepository by lazy { CatalogRepository(applicationContext) }
    private val geoRepository by lazy { GeoRepository(applicationContext) }
    private val discoveryRepository = DiscoveryRepository()

    private var regions: List<PlateRegion> = emptyList()
    private var geoShapes: List<GeoShape> = emptyList()
    private var germanyStateShapes: List<GeoShape> = emptyList()
    private var discovered: Set<String> = emptySet()
    private var showGlobal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        country = Country.fromId(intent.getStringExtra(KennzeichenHomeActivity.EXTRA_COUNTRY))
        binding = ActivityKennzeichenMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        title = getString(R.string.kennzeichen_map_title, country.displayName)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toggleScope.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showGlobal = checkedId == binding.buttonScopeGlobal.id
            render()
        }

        if (country == Country.GERMANY) {
            binding.textMapNote.isVisible = true
            binding.textMapNote.text = getString(R.string.kennzeichen_map_germany_note)
        }

        lifecycleScope.launch {
            regions = catalogRepository.regionsFor(country)
            geoShapes = country.geoFile?.let { geoRepository.load(it) } ?: emptyList()
            germanyStateShapes = if (country == Country.GERMANY) {
                geoRepository.load("geo/de_bundeslaender.geojson")
            } else {
                emptyList()
            }
            render()
        }

        lifecycleScope.launch {
            val uid = authRepository.currentUser?.uid
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discoveryRepository.observeDiscoveries(country).collect { discoveries ->
                    globalCache = discoveries.map { it.code }.toSet()
                    personalCache = discoveries.filter { it.uid == uid }.map { it.code }.toSet()
                    render()
                }
            }
        }
    }

    private var personalCache: Set<String> = emptySet()
    private var globalCache: Set<String> = emptySet()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun render() {
        discovered = if (showGlobal) globalCache else personalCache
        val discoveredColor = ContextCompat.getColor(this, R.color.map_discovered)
        val undiscoveredColor = ContextCompat.getColor(this, R.color.map_undiscovered)

        when (country) {
            Country.GERMANY -> {
                val progressByState = stateProgress()
                binding.mapView.setData(germanyStateShapes) { shape ->
                    val fraction = progressByState[shape.code] ?: 0f
                    ColorUtils.blendARGB(undiscoveredColor, discoveredColor, fraction)
                }
                binding.chipGroupOutremer.isVisible = false
                binding.textOutremerTitle.isVisible = false
            }
            Country.FRANCE -> {
                val metropole = geoShapes.filter { it.code.length == 2 }
                val outremer = geoShapes.filter { it.code.length == 3 }
                binding.mapView.setData(metropole) { shape ->
                    if (shape.code in discovered) discoveredColor else undiscoveredColor
                }
                binding.chipGroupOutremer.removeAllViews()
                if (outremer.isNotEmpty()) {
                    binding.textOutremerTitle.isVisible = true
                    binding.chipGroupOutremer.isVisible = true
                    outremer.forEach { shape ->
                        val found = shape.code in discovered
                        val chip = Chip(this).apply {
                            text = "${shape.code} ${shape.name}"
                            setChipBackgroundColorResource(
                                if (found) R.color.map_discovered else R.color.map_undiscovered,
                            )
                            if (found) setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        }
                        binding.chipGroupOutremer.addView(chip)
                    }
                } else {
                    binding.textOutremerTitle.isVisible = false
                    binding.chipGroupOutremer.isVisible = false
                }
            }
            else -> {
                binding.mapView.setData(geoShapes) { shape ->
                    if (shape.code in discovered) discoveredColor else undiscoveredColor
                }
                binding.chipGroupOutremer.isVisible = false
                binding.textOutremerTitle.isVisible = false
            }
        }

        binding.textMapProgress.text =
            getString(R.string.kennzeichen_progress_summary, discovered.size, regions.size)
    }

    private fun stateProgress(): Map<String, Float> =
        regions.filter { it.stateCode != null }
            .groupBy { it.stateCode!! }
            .mapValues { (_, regionsInState) ->
                val found = regionsInState.count { it.code in discovered }
                found / regionsInState.size.toFloat()
            }

    companion object {
        fun intent(context: Context, country: Country): Intent =
            Intent(context, KennzeichenMapActivity::class.java)
                .putExtra(KennzeichenHomeActivity.EXTRA_COUNTRY, country.id)
    }
}
