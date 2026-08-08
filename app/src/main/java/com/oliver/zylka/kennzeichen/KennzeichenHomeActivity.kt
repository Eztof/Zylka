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
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.CatalogRepository
import com.oliver.zylka.data.kennzeichen.Country
import com.oliver.zylka.data.kennzeichen.Discovery
import com.oliver.zylka.data.kennzeichen.DiscoveryRepository
import com.oliver.zylka.data.kennzeichen.PlateRegion
import com.oliver.zylka.databinding.ActivityKennzeichenHomeBinding
import com.oliver.zylka.databinding.ItemActionCardBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Landing screen of the Kennzeichen-Sammelspiel: pick a country, see progress, jump into
 * entering a find, browsing the collection, the map, the shared collection, or the feed.
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
        setSupportActionBar(binding.toolbar)
        title = getString(R.string.kennzeichen_home_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Country.entries.forEach { c ->
            val chip = layoutInflater.inflate(R.layout.item_country_chip, binding.chipGroupCountry, false) as Chip
            chip.text = "${c.flagEmoji} ${c.displayName}"
            chip.id = android.view.View.generateViewId()
            chip.tag = c.id
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

        setUpActionCard(
            binding.cardEntry, R.drawable.ic_add_circle,
            R.string.kennzeichen_action_entry_title, R.string.kennzeichen_action_entry_subtitle,
        ) { startActivity(KennzeichenEntryActivity.intent(this, country)) }

        setUpActionCard(
            binding.cardCollection, R.drawable.ic_list,
            R.string.kennzeichen_action_collection_title, R.string.kennzeichen_action_collection_subtitle,
        ) { startActivity(KennzeichenCollectionActivity.intent(this, country, global = false)) }

        setUpActionCard(
            binding.cardMap, R.drawable.ic_map,
            R.string.kennzeichen_action_map_title, R.string.kennzeichen_action_map_subtitle,
        ) { startActivity(KennzeichenMapActivity.intent(this, country)) }

        setUpActionCard(
            binding.cardGlobal, R.drawable.ic_public,
            R.string.kennzeichen_action_global_title, R.string.kennzeichen_action_global_subtitle,
        ) { startActivity(KennzeichenCollectionActivity.intent(this, country, global = true)) }

        setUpActionCard(
            binding.cardHistory, R.drawable.ic_history,
            R.string.kennzeichen_action_history_title, R.string.kennzeichen_action_history_subtitle,
        ) { startActivity(KennzeichenHistoryActivity.intent(this, country)) }

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
            updateProgress(emptyList())

            val uid = authRepository.currentUser?.uid
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discoveryRepository.observeDiscoveries(country).collect { discoveries ->
                    updateProgress(discoveries, uid)
                }
            }
        }
    }

    private fun updateProgress(discoveries: List<Discovery>, uid: String? = authRepository.currentUser?.uid) {
        val personal = discoveries.filter { it.uid == uid }.map { it.code }.distinct().size
        val global = discoveries.map { it.code }.distinct().size
        val total = regions.size.coerceAtLeast(1)
        binding.progressPersonal.progress = (personal * 100 / total)
        binding.textProgressPersonal.text = getString(R.string.kennzeichen_progress_summary, personal, regions.size)
        binding.progressGlobal.progress = (global * 100 / total)
        binding.textProgressGlobal.text = getString(R.string.kennzeichen_progress_summary, global, regions.size)
    }

    private fun setUpActionCard(
        card: ItemActionCardBinding,
        @androidx.annotation.DrawableRes icon: Int,
        @androidx.annotation.StringRes title: Int,
        @androidx.annotation.StringRes subtitle: Int,
        onClick: () -> Unit,
    ) {
        card.iconAction.setImageResource(icon)
        card.textActionTitle.setText(title)
        card.textActionSubtitle.setText(subtitle)
        card.root.setOnClickListener { onClick() }
    }

    companion object {
        const val EXTRA_COUNTRY = "country"
        const val EXTRA_GLOBAL = "global"

        fun intent(context: Context): Intent = Intent(context, KennzeichenHomeActivity::class.java)
    }
}
