package com.oliver.zylka.regel

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.regel.RegelCalculator
import com.oliver.zylka.data.regel.RegelEintrag
import com.oliver.zylka.data.regel.RegelPrognose
import com.oliver.zylka.data.regel.RegelRepository
import com.oliver.zylka.databinding.ActivityRegelkalenderBinding
import com.oliver.zylka.databinding.DialogRegelDayEditBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Monatskalender für den Regelkalender: Tage antippen setzt die Intensität (0-10, siehe
 * [RegelDayAdapter]/[RegelRepository]). Künftige Tage, für die [RegelCalculator] eine Periode
 * vorhersagt, werden im Grid nur umrandet dargestellt (nie mit echten Einträgen verwechselbar) -
 * das ist die einzige Prognose-Anzeige, es gibt keinen zusätzlichen Text dazu. Global/geteilt
 * zwischen allen eingeloggten Nutzern, wie `SensorsActivity`.
 */
class RegelkalenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegelkalenderBinding
    private val authRepository = AuthRepository()
    private val regelRepository = RegelRepository()
    private val dayAdapter = RegelDayAdapter { datum -> zeigeEditor(datum) }

    private val monatsFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
    private val titelFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)

    private var alleEintraege: List<RegelEintrag> = emptyList()
    private var aktuellePrognose: RegelPrognose? = null
    private var sichtbarerMonat: YearMonth = YearMonth.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegelkalenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerDays.layoutManager = GridLayoutManager(this, 7)
        binding.recyclerDays.adapter = dayAdapter
        binding.buttonPrevMonth.setOnClickListener {
            sichtbarerMonat = sichtbarerMonat.minusMonths(1)
            renderMonat()
        }
        binding.buttonNextMonth.setOnClickListener {
            sichtbarerMonat = sichtbarerMonat.plusMonths(1)
            renderMonat()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                regelRepository.observeEntries().collect { eintraege ->
                    alleEintraege = eintraege
                    val perioden = RegelCalculator.ermittlePerioden(eintraege)
                    aktuellePrognose = RegelCalculator.berechnePrognose(perioden)
                    renderMonat()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun renderMonat() {
        binding.textMonth.text = monatsFormat.format(sichtbarerMonat).replaceFirstChar { it.uppercase() }

        val eintraegeMap = alleEintraege.associateBy { it.datum }
        val prognoseMap = aktuellePrognose?.perioden.orEmpty().flatMap { it.tage }.toMap()
        val heute = LocalDate.now()

        val ersterTag = sichtbarerMonat.atDay(1)
        val anzahlTage = sichtbarerMonat.lengthOfMonth()
        // DayOfWeek.MONDAY.value == 1 -> 0 Lückenzellen, wenn der Monat mit einem Montag beginnt.
        val startOffset = ersterTag.dayOfWeek.value - 1

        val zellen = mutableListOf<RegelTagZelle?>()
        repeat(startOffset) { zellen.add(null) }
        for (tag in 1..anzahlTage) {
            val datum = sichtbarerMonat.atDay(tag)
            zellen.add(
                RegelTagZelle(
                    datum = datum,
                    echteIntensitaet = eintraegeMap[datum]?.intensitaet,
                    prognoseIntensitaet = prognoseMap[datum],
                    istHeute = datum == heute,
                ),
            )
        }
        while (zellen.size % 7 != 0) zellen.add(null)
        dayAdapter.submitList(zellen)
    }

    private fun zeigeEditor(datum: LocalDate) {
        val uid = authRepository.currentUser?.uid ?: return
        val dialogBinding = DialogRegelDayEditBinding.inflate(layoutInflater)
        val aktuelleIntensitaet = alleEintraege.firstOrNull { it.datum == datum }?.intensitaet ?: 0

        dialogBinding.sliderIntensitaet.value = aktuelleIntensitaet.toFloat()
        dialogBinding.textLevel.text = getString(R.string.regel_dialog_level, aktuelleIntensitaet)
        dialogBinding.sliderIntensitaet.addOnChangeListener { _, value, _ ->
            dialogBinding.textLevel.text = getString(R.string.regel_dialog_level, value.toInt())
        }

        AlertDialog.Builder(this)
            .setTitle(datum.format(titelFormat).replaceFirstChar { it.uppercase() })
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val neueIntensitaet = dialogBinding.sliderIntensitaet.value.toInt()
                lifecycleScope.launch { regelRepository.setIntensitaet(uid, datum, neueIntensitaet) }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
