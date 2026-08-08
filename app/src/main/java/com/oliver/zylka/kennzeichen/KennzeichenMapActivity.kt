package com.oliver.zylka.kennzeichen

import com.oliver.zylka.util.applyStatusBarTopInset
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.CatalogRepository
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.Discovery
import com.oliver.zylka.data.kennzeichen.DiscoveryRepository
import com.oliver.zylka.data.kennzeichen.GeoRepository
import com.oliver.zylka.data.kennzeichen.GeoShape
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ActivityKennzeichenMapBinding
import com.oliver.zylka.databinding.DialogKreisInfoBinding
import com.oliver.zylka.databinding.ItemKreisInfoCodeBinding
import kotlinx.coroutines.launch

/**
 * Real, zoomable/pannable/tappable geographic map of a country's regions, shaded by what's
 * been discovered. For Germany this is the full set of ~400 Landkreise/kreisfreie Städte;
 * tapping a region shows which of its codes are found and by whom/when.
 */
class KennzeichenMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKennzeichenMapBinding
    private lateinit var country: Country
    private val authRepository = AuthRepository()
    private val catalogRepository by lazy { CatalogRepository(applicationContext) }
    private val geoRepository by lazy { GeoRepository(applicationContext) }
    private val discoveryRepository = DiscoveryRepository()

    private var regions: List<PlateRegion> = emptyList()
    private var geoShapes: List<GeoShape> = emptyList()
    private var discoveries: List<Discovery> = emptyList()
    private var discovered: Set<String> = emptySet()
    private var personalCache: Set<String> = emptySet()
    private var globalCache: Set<String> = emptySet()
    private var showGlobal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        country = Country.fromId(intent.getStringExtra(KennzeichenHomeActivity.EXTRA_COUNTRY))
        binding = ActivityKennzeichenMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(R.string.kennzeichen_map_title, country.displayName)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toggleScope.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showGlobal = checkedId == binding.buttonScopeGlobal.id
            render()
        }

        binding.mapView.setOnShapeTapped { shape -> showKreisInfo(shape) }

        lifecycleScope.launch {
            regions = catalogRepository.regionsFor(country)
            geoShapes = country.geoFile?.let { geoRepository.load(it) } ?: emptyList()
            render()
        }

        lifecycleScope.launch {
            val uid = authRepository.currentUser?.uid
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discoveryRepository.observeDiscoveries(country).collect { list ->
                    discoveries = list
                    globalCache = list.map { it.code }.toSet()
                    personalCache = list.filter { it.uid == uid }.map { it.code }.toSet()
                    render()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun render() {
        discovered = if (showGlobal) globalCache else personalCache
        val discoveredColor = ContextCompat.getColor(this, R.color.map_discovered)
        val partialColor = ContextCompat.getColor(this, R.color.map_discovered_partial)
        val undiscoveredColor = ContextCompat.getColor(this, R.color.map_undiscovered)

        fun colorFor(shape: GeoShape): Int {
            val found = shape.codes.count { it in discovered }
            return when {
                found == 0 -> undiscoveredColor
                found == shape.codes.size -> discoveredColor
                else -> partialColor
            }
        }

        when (country) {
            Country.FRANCE -> {
                val metropole = geoShapes.filter { it.codes.first().length == 2 }
                val outremer = geoShapes.filter { it.codes.first().length == 3 }
                binding.mapView.setData(metropole) { colorFor(it) }
                binding.chipGroupOutremer.removeAllViews()
                if (outremer.isNotEmpty()) {
                    binding.textOutremerTitle.isVisible = true
                    binding.chipGroupOutremer.isVisible = true
                    outremer.forEach { shape ->
                        val found = shape.codes.any { it in discovered }
                        val chip = layoutInflater.inflate(
                            R.layout.item_country_chip, binding.chipGroupOutremer, false,
                        ) as Chip
                        chip.text = "${shape.codes.first()} ${shape.name}"
                        chip.isCheckable = false
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                            if (found) discoveredColor else undiscoveredColor,
                        )
                        chip.setTextColor(
                            if (found) ContextCompat.getColor(this, android.R.color.white) else ContextCompat.getColor(this, R.color.brand_on_surface_variant),
                        )
                        chip.setOnClickListener { showKreisInfo(shape) }
                        binding.chipGroupOutremer.addView(chip)
                    }
                } else {
                    binding.textOutremerTitle.isVisible = false
                    binding.chipGroupOutremer.isVisible = false
                }
            }
            else -> {
                binding.mapView.setData(geoShapes) { colorFor(it) }
                binding.chipGroupOutremer.isVisible = false
                binding.textOutremerTitle.isVisible = false
            }
        }

        binding.textMapProgress.text = getString(R.string.kennzeichen_progress_summary, discovered.size, regions.size)
    }

    private fun showKreisInfo(shape: GeoShape) {
        val dialogBinding = DialogKreisInfoBinding.inflate(LayoutInflater.from(this))
        dialogBinding.textKreisName.text = shape.name
        dialogBinding.textKreisState.text = shape.state
        dialogBinding.textKreisState.isVisible = !shape.state.isNullOrBlank()

        dialogBinding.containerCodes.removeAllViews()
        val uid = authRepository.currentUser?.uid
        shape.codes.sorted().forEach { code ->
            val row = ItemKreisInfoCodeBinding.inflate(LayoutInflater.from(this), dialogBinding.containerCodes, false)
            row.plateBadge.textPlateCountry.text = country.badgeLetter
            row.plateBadge.textPlateCode.text = code
            val match = discoveries.filter { it.code == code }.maxByOrNull { it.discoveredAt?.time ?: 0L }
            row.textStatus.text = if (match != null) {
                val who = if (match.uid == uid) getString(R.string.kennzeichen_history_you) else match.userLabel
                val whenText = match.discoveredAt?.let {
                    DateUtils.getRelativeTimeSpanString(it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                } ?: getString(R.string.kennzeichen_history_just_now)
                getString(R.string.kennzeichen_history_meta, who, whenText)
            } else {
                getString(R.string.kennzeichen_not_found_yet)
            }
            dialogBinding.containerCodes.addView(row.root)
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        fun intent(context: Context, country: Country): Intent =
            Intent(context, KennzeichenMapActivity::class.java)
                .putExtra(KennzeichenHomeActivity.EXTRA_COUNTRY, country.id)
    }
}
