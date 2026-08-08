package com.oliver.zylka.kennzeichen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.CatalogRepository
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.DiscoveryRepository
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ActivityKennzeichenHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Landing screen of the Kennzeichen-Sammelspiel: pick a country, see progress, jump into
 * entering a find, browsing the collection, the map, or the shared global collection.
 */
class KennzeichenHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKennzeichenHomeBinding
    private val authRepository = AuthRepository()
    private val catalogRepository by lazy { CatalogRepository(applicationContext) }
    private val discoveryRepository = DiscoveryRepository()

    private var country: Country = Country.GERMANY
    private var regions: List<PlateRegion> = emptyList()
    private var dataJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKennzeichenHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(com.oliver.zylka.R.string.kennzeichen_home_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Country.entries.forEach { c ->
            val chip = Chip(this).apply {
                text = "${c.flagEmoji} ${c.displayName}"
                isCheckable = true
                id = android.view.View.generateViewId()
                tag = c.id
            }
            binding.chipGroupCountry.addView(chip)
        }
        (binding.chipGroupCountry.getChildAt(0) as Chip).isChecked = true

        binding.chipGroupCountry.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId)
            val selected = Country.fromId(chip?.tag as? String)
            if (selected != country) {
                country = selected
                loadCountry()
            }
        }

        binding.cardEntry.setOnClickListener {
            startActivity(KennzeichenEntryActivity.intent(this, country))
        }
        binding.cardCollection.setOnClickListener {
            startActivity(KennzeichenCollectionActivity.intent(this, country, global = false))
        }
        binding.cardMap.setOnClickListener {
            startActivity(KennzeichenMapActivity.intent(this, country))
        }
        binding.cardGlobal.setOnClickListener {
            startActivity(KennzeichenCollectionActivity.intent(this, country, global = true))
        }

        loadCountry()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadCountry() {
        dataJob?.cancel()
        binding.groupProgress.isVisible = false
        binding.progressLoading.isVisible = true

        dataJob = lifecycleScope.launch {
            regions = catalogRepository.regionsFor(country)
            binding.progressLoading.isVisible = false
            binding.groupProgress.isVisible = true
            updateProgress(personalCount = 0, globalCount = 0)

            val uid = authRepository.currentUser?.uid
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    discoveryRepository.globalCodes(country).collect { codes ->
                        updateProgress(globalCount = codes.size)
                    }
                }
                if (uid != null) {
                    launch {
                        discoveryRepository.personalCodes(uid, country).collect { codes ->
                            updateProgress(personalCount = codes.size)
                        }
                    }
                }
            }
        }
    }

    private var lastPersonal = 0
    private var lastGlobal = 0

    private fun updateProgress(personalCount: Int? = null, globalCount: Int? = null) {
        personalCount?.let { lastPersonal = it }
        globalCount?.let { lastGlobal = it }
        val total = regions.size.coerceAtLeast(1)
        binding.progressPersonal.progress = (lastPersonal * 100 / total)
        binding.textProgressPersonal.text = "$lastPersonal / ${regions.size}"
        binding.progressGlobal.progress = (lastGlobal * 100 / total)
        binding.textProgressGlobal.text = "$lastGlobal / ${regions.size}"
    }

    companion object {
        const val EXTRA_COUNTRY = "country"
        const val EXTRA_GLOBAL = "global"

        fun intent(context: Context): Intent = Intent(context, KennzeichenHomeActivity::class.java)
    }
}
