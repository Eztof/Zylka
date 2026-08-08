package com.oliver.zylka.kennzeichen

import com.oliver.zylka.util.applyStatusBarTopInset
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ActivityKennzeichenCollectionBinding
import kotlinx.coroutines.launch

/** Read-only browse of every code for a country, either the player's own or everyone's. */
class KennzeichenCollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKennzeichenCollectionBinding
    private lateinit var country: Country
    private var global: Boolean = false
    private val authRepository = AuthRepository()
    private val catalogRepository by lazy { CatalogRepository(applicationContext) }
    private val discoveryRepository = DiscoveryRepository()
    private val adapter = PlateRegionAdapter { }

    private var allRegions: List<PlateRegion> = emptyList()
    private var foundCodes: Set<String> = emptySet()
    private var query: String = ""
    private var onlyMissing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        country = Country.fromId(intent.getStringExtra(KennzeichenHomeActivity.EXTRA_COUNTRY))
        global = intent.getBooleanExtra(KennzeichenHomeActivity.EXTRA_GLOBAL, false)
        binding = ActivityKennzeichenCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(
            if (global) R.string.kennzeichen_collection_global_title else R.string.kennzeichen_collection_personal_title,
        )
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.chipOnlyMissing.setOnCheckedChangeListener { _, checked ->
            onlyMissing = checked
            renderList()
        }
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discoveryRepository.observeDiscoveries(country).collect { discoveries ->
                    foundCodes = if (global) {
                        discoveries.map { it.code }.toSet()
                    } else {
                        discoveries.filter { it.uid == uid }.map { it.code }.toSet()
                    }
                    renderList()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun renderList() {
        val q = query.trim().lowercase()
        val filtered = allRegions.filter { region ->
            val matchesQuery = q.isEmpty() || region.code.lowercase().startsWith(q) || region.name.lowercase().contains(q)
            val matchesMissing = !onlyMissing || region.code !in foundCodes
            matchesQuery && matchesMissing
        }.sortedWith(
            compareByDescending<PlateRegion> { it.code in foundCodes }.thenBy { it.code },
        )
        adapter.submitList(filtered.map { PlateRegionRow(it, it.code in foundCodes) })

        val total = allRegions.size.coerceAtLeast(1)
        binding.progressCollection.progress = (foundCodes.size.coerceAtMost(allRegions.size) * 100 / total)
        binding.textProgressCollection.text =
            getString(R.string.kennzeichen_progress_summary, foundCodes.size, allRegions.size)
    }

    companion object {
        fun intent(context: Context, country: Country, global: Boolean): Intent =
            Intent(context, KennzeichenCollectionActivity::class.java)
                .putExtra(KennzeichenHomeActivity.EXTRA_COUNTRY, country.id)
                .putExtra(KennzeichenHomeActivity.EXTRA_GLOBAL, global)
    }
}
