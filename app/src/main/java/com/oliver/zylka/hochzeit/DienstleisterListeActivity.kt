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
import com.oliver.zylka.data.hochzeit.Dienstleister
import com.oliver.zylka.data.hochzeit.DienstleisterKategorie
import com.oliver.zylka.data.hochzeit.DienstleisterRepository
import com.oliver.zylka.data.hochzeit.DienstleisterStatus
import com.oliver.zylka.databinding.ActivityDienstleisterListeBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.util.Locale

/** Dienstleister-/Kostenposten-Liste mit Textsuche plus Kategorie-/Status-Filterchips
 * (Mehrfachauswahl, leere Auswahl = kein Filter - gleiche Struktur wie [GastListeActivity]),
 * Summenzeile inklusive Kosten-Summe aller [DienstleisterStatus.GEBUCHT]-Einträge. Tippen öffnet
 * [DienstleisterEditActivity]. */
class DienstleisterListeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDienstleisterListeBinding
    private val dienstleisterRepository = DienstleisterRepository()
    private val adapter = DienstleisterAdapter { eintrag ->
        startActivity(DienstleisterEditActivity.intent(this, eintrag.id))
    }

    private var alleEintraege: List<Dienstleister> = emptyList()
    private var query: String = ""
    private var kategorieFilter: Set<DienstleisterKategorie> = emptySet()
    private var statusFilter: Set<DienstleisterStatus> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDienstleisterListeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(R.string.hochzeit_dienstleister_liste_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerDienstleister.layoutManager = LinearLayoutManager(this)
        binding.recyclerDienstleister.adapter = adapter

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
        binding.chipGroupStatus.setOnCheckedStateChangeListener { group, _ ->
            statusFilter = group.checkedChipIds.mapNotNull { id -> statusForChipId(id) }.toSet()
            renderList()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dienstleisterRepository.observeDienstleister().collect { eintraege ->
                    alleEintraege = eintraege.sortedBy { it.name.lowercase() }
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
        menuInflater.inflate(R.menu.menu_dienstleister_liste, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_dienstleister) {
            startActivity(DienstleisterEditActivity.intent(this, dienstleisterId = null))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun renderList() {
        val q = query.trim().lowercase()
        val gefiltert = alleEintraege.filter { eintrag ->
            val matchesQuery = q.isEmpty() || eintrag.name.lowercase().contains(q)
            val matchesKategorie = kategorieFilter.isEmpty() || eintrag.kategorie in kategorieFilter
            val matchesStatus = statusFilter.isEmpty() || eintrag.status in statusFilter
            matchesQuery && matchesKategorie && matchesStatus
        }
        adapter.submitList(gefiltert)
        binding.textEmpty.isVisible = gefiltert.isEmpty()

        val gebucht = alleEintraege.count { it.status == DienstleisterStatus.GEBUCHT }
        val offen = alleEintraege.count {
            it.status != DienstleisterStatus.GEBUCHT && it.status != DienstleisterStatus.ABGESAGT
        }
        val summeGebucht = alleEintraege
            .filter { it.status == DienstleisterStatus.GEBUCHT }
            .sumOf { it.kostenEuro ?: 0.0 }
        binding.textSummary.text = getString(
            R.string.hochzeit_dienstleister_summary,
            alleEintraege.size,
            gebucht,
            offen,
            String.format(Locale.GERMANY, "%.2f", summeGebucht),
        )
    }

    private fun kategorieForChipId(chipId: Int): DienstleisterKategorie? = when (chipId) {
        binding.chipKategorieDj.id -> DienstleisterKategorie.DJ
        binding.chipKategorieCatering.id -> DienstleisterKategorie.CATERING
        binding.chipKategorieFotograf.id -> DienstleisterKategorie.FOTOGRAF
        binding.chipKategorieBlumen.id -> DienstleisterKategorie.BLUMEN
        binding.chipKategorieLocation.id -> DienstleisterKategorie.LOCATION
        binding.chipKategorieTorte.id -> DienstleisterKategorie.TORTE
        binding.chipKategorieDekoration.id -> DienstleisterKategorie.DEKORATION
        binding.chipKategorieTransport.id -> DienstleisterKategorie.TRANSPORT
        binding.chipKategorieBrautmode.id -> DienstleisterKategorie.BRAUTMODE
        binding.chipKategorieSonstiges.id -> DienstleisterKategorie.SONSTIGES
        else -> null
    }

    private fun statusForChipId(chipId: Int): DienstleisterStatus? = when (chipId) {
        binding.chipStatusIdee.id -> DienstleisterStatus.IDEE
        binding.chipStatusAngefragt.id -> DienstleisterStatus.ANGEFRAGT
        binding.chipStatusAngebotErhalten.id -> DienstleisterStatus.ANGEBOT_ERHALTEN
        binding.chipStatusGebucht.id -> DienstleisterStatus.GEBUCHT
        binding.chipStatusAbgesagt.id -> DienstleisterStatus.ABGESAGT
        else -> null
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, DienstleisterListeActivity::class.java)
    }
}
