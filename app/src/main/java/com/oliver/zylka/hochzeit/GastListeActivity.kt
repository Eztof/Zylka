package com.oliver.zylka.hochzeit

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
import com.oliver.zylka.data.hochzeit.Gast
import com.oliver.zylka.data.hochzeit.GastKategorie
import com.oliver.zylka.data.hochzeit.GastPrioritaet
import com.oliver.zylka.data.hochzeit.GastRepository
import com.oliver.zylka.databinding.ActivityGastListeBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Gästeliste mit Textsuche plus Kategorie-/Prioritäts-Filterchips (Mehrfachauswahl - leere
 * Auswahl bedeutet kein Filter in dieser Dimension, Vorbild `KennzeichenCollectionActivity`s
 * kombiniertes Suche+Filter). Tippen öffnet [GastEditActivity]. */
class GastListeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGastListeBinding
    private val gastRepository = GastRepository()
    private val adapter = GastAdapter { gast -> startActivity(GastEditActivity.intent(this, gast.id)) }

    private var alleGaeste: List<Gast> = emptyList()
    private var query: String = ""
    private var kategorieFilter: Set<GastKategorie> = emptySet()
    private var prioritaetFilter: Set<GastPrioritaet> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGastListeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(R.string.hochzeit_gast_liste_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerGaeste.layoutManager = LinearLayoutManager(this)
        binding.recyclerGaeste.adapter = adapter

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                renderList()
            }
        })

        binding.chipGroupKategorie.setOnCheckedStateChangeListener { group, _ ->
            kategorieFilter = group.checkedChipIds.mapNotNull { id -> kategorieForChipId(id) }.toSet()
            renderList()
        }
        binding.chipGroupPrioritaet.setOnCheckedStateChangeListener { group, _ ->
            prioritaetFilter = group.checkedChipIds.mapNotNull { id -> prioritaetForChipId(id) }.toSet()
            renderList()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                gastRepository.observeGaeste().collect { gaeste ->
                    alleGaeste = gaeste.sortedBy { it.name.lowercase() }
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
        menuInflater.inflate(R.menu.menu_gast_liste, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_gast) {
            startActivity(GastEditActivity.intent(this, gastId = null))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun renderList() {
        val q = query.trim().lowercase()
        val gefiltert = alleGaeste.filter { gast ->
            val matchesQuery = q.isEmpty() || gast.name.lowercase().contains(q)
            val matchesKategorie = kategorieFilter.isEmpty() || gast.kategorie in kategorieFilter
            val matchesPrioritaet = prioritaetFilter.isEmpty() || gast.prioritaet in prioritaetFilter
            matchesQuery && matchesKategorie && matchesPrioritaet
        }
        adapter.submitList(gefiltert)
        binding.textEmpty.isVisible = gefiltert.isEmpty()

        binding.textSummary.text = getString(
            R.string.hochzeit_gast_summary,
            alleGaeste.size,
            alleGaeste.count { it.prioritaet == GastPrioritaet.SICHER },
            alleGaeste.count { it.prioritaet == GastPrioritaet.MITTEL },
            alleGaeste.count { it.prioritaet == GastPrioritaet.MAEH },
        )
    }

    private fun kategorieForChipId(chipId: Int): GastKategorie? = when (chipId) {
        binding.chipKategorieFreunde.id -> GastKategorie.FREUNDE
        binding.chipKategorieFamilie.id -> GastKategorie.FAMILIE
        binding.chipKategorieArbeitskollegen.id -> GastKategorie.ARBEITSKOLLEGEN
        else -> null
    }

    private fun prioritaetForChipId(chipId: Int): GastPrioritaet? = when (chipId) {
        binding.chipPrioritaetSicher.id -> GastPrioritaet.SICHER
        binding.chipPrioritaetMittel.id -> GastPrioritaet.MITTEL
        binding.chipPrioritaetMaeh.id -> GastPrioritaet.MAEH
        else -> null
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, GastListeActivity::class.java)
    }
}
