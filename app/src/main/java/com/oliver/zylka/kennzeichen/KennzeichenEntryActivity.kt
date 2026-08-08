package com.oliver.zylka.kennzeichen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.CatalogRepository
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.DiscoveryRepository
import com.oliver.zylka.data.kennzeichen.LocationHelper
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ActivityKennzeichenEntryBinding
import kotlinx.coroutines.launch

/** Search for a spotted plate's code and mark it as found. */
class KennzeichenEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKennzeichenEntryBinding
    private lateinit var country: Country
    private val authRepository = AuthRepository()
    private val catalogRepository by lazy { CatalogRepository(applicationContext) }
    private val discoveryRepository = DiscoveryRepository()
    private val locationHelper by lazy { LocationHelper(applicationContext) }
    private val adapter = PlateRegionAdapter { onRegionTapped(it) }

    private var allRegions: List<PlateRegion> = emptyList()
    private var foundCodes: Set<String> = emptySet()
    private var query: String = ""
    private var locationAsked = false

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Ergebnis wird bei recordDiscovery erneut über LocationHelper geprüft. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        country = Country.fromId(intent.getStringExtra(KennzeichenHomeActivity.EXTRA_COUNTRY))
        binding = ActivityKennzeichenEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        title = getString(R.string.kennzeichen_entry_title, country.displayName)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                renderList()
            }
        })

        lifecycleScope.launch {
            allRegions = catalogRepository.regionsFor(country)
            renderList()
        }

        val uid = authRepository.currentUser?.uid
        if (uid != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    discoveryRepository.observeDiscoveries(country).collect { discoveries ->
                        foundCodes = discoveries.filter { it.uid == uid }.map { it.code }.toSet()
                        renderList()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun onRegionTapped(region: PlateRegion) {
        val user = authRepository.currentUser ?: return
        if (region.code in foundCodes) {
            Toast.makeText(this, getString(R.string.kennzeichen_already_found_toast, region.code), Toast.LENGTH_SHORT).show()
            return
        }
        maybeRequestLocationPermission()
        val userLabel = user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: getString(R.string.kennzeichen_unknown_user)
        lifecycleScope.launch {
            val location = locationHelper.currentLocationOrNull()
            discoveryRepository.recordDiscovery(user.uid, userLabel, region, location?.first, location?.second)
            Toast.makeText(
                this@KennzeichenEntryActivity,
                getString(R.string.kennzeichen_discovered_toast, region.code, region.name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun maybeRequestLocationPermission() {
        if (locationAsked || locationHelper.hasPermission()) return
        locationAsked = true
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun renderList() {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allRegions
        } else {
            allRegions.filter { it.code.lowercase().startsWith(q) || it.name.lowercase().contains(q) }
        }
        adapter.submitList(filtered.map { PlateRegionRow(it, it.code in foundCodes) })
    }

    companion object {
        fun intent(context: Context, country: Country): Intent =
            Intent(context, KennzeichenEntryActivity::class.java)
                .putExtra(KennzeichenHomeActivity.EXTRA_COUNTRY, country.id)
    }
}
