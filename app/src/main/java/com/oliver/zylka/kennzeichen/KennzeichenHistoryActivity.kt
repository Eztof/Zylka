package com.oliver.zylka.kennzeichen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.DiscoveryRepository
import com.oliver.zylka.databinding.ActivityKennzeichenHistoryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Chronological "wer hat wann was wo entdeckt" feed for a country. */
class KennzeichenHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKennzeichenHistoryBinding
    private val authRepository = AuthRepository()
    private val discoveryRepository = DiscoveryRepository()
    private lateinit var adapter: HistoryAdapter

    private var country: Country = Country.GERMANY
    private var dataJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        country = Country.fromId(intent.getStringExtra(KennzeichenHomeActivity.EXTRA_COUNTRY))
        binding = ActivityKennzeichenHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        title = getString(R.string.kennzeichen_action_history_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = HistoryAdapter(authRepository.currentUser?.uid)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        Country.entries.forEach { c ->
            val chip = layoutInflater.inflate(R.layout.item_country_chip, binding.chipGroupCountry, false) as Chip
            chip.text = "${c.flagEmoji} ${c.displayName}"
            chip.id = android.view.View.generateViewId()
            chip.tag = c.id
            binding.chipGroupCountry.addView(chip)
            if (c == country) chip.isChecked = true
        }
        binding.chipGroupCountry.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId)
            val selected = Country.fromId(chip?.tag as? String)
            if (selected != country) {
                country = selected
                loadCountry()
            }
        }

        loadCountry()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadCountry() {
        dataJob?.cancel()
        dataJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discoveryRepository.observeDiscoveries(country).collect { discoveries ->
                    adapter.submitList(discoveries)
                }
            }
        }
    }

    companion object {
        fun intent(context: Context, country: Country): Intent =
            Intent(context, KennzeichenHistoryActivity::class.java)
                .putExtra(KennzeichenHomeActivity.EXTRA_COUNTRY, country.id)
    }
}
