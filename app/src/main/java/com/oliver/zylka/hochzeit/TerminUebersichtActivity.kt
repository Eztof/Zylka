package com.oliver.zylka.hochzeit

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.hochzeit.TerminOption
import com.oliver.zylka.data.hochzeit.TerminRepository
import com.oliver.zylka.data.hochzeit.TerminStatus
import com.oliver.zylka.data.hochzeit.TerminTyp
import com.oliver.zylka.databinding.ActivityTerminUebersichtBinding
import com.oliver.zylka.databinding.DialogTerminEditBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Drei unabhängige Kandidatenlisten für Standesamt/Kirche/Location (siehe [TerminTyp]) - bewusst
 * kein einzelnes "Hochzeitsdatum", da diese in der Praxis oft an unterschiedlichen Tagen liegen.
 * Editieren läuft über einen Dialog statt einer eigenen Activity (siehe [zeigeEditor]), analog zu
 * `RegelkalenderActivity.zeigeEditor` - passt zur Größe eines Termin-Eintrags und bleibt ohne
 * Screen-Wechsel "extrem intuitiv". Global/geteilt zwischen allen eingeloggten Nutzern.
 */
class TerminUebersichtActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminUebersichtBinding
    private val authRepository = AuthRepository()
    private val terminRepository = TerminRepository()

    private val adapterStandesamt = TerminAdapter { zeigeEditor(TerminTyp.STANDESAMT, it) }
    private val adapterKirche = TerminAdapter { zeigeEditor(TerminTyp.KIRCHE, it) }
    private val adapterLocation = TerminAdapter { zeigeEditor(TerminTyp.LOCATION, it) }

    private val titelFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminUebersichtBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(R.string.hochzeit_termin_uebersicht_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setUpSection(binding.recyclerStandesamt, adapterStandesamt, binding.textTitleStandesamt, TerminTyp.STANDESAMT)
        setUpSection(binding.recyclerKirche, adapterKirche, binding.textTitleKirche, TerminTyp.KIRCHE)
        setUpSection(binding.recyclerLocation, adapterLocation, binding.textTitleLocation, TerminTyp.LOCATION)

        binding.buttonAddStandesamt.setOnClickListener { zeigeEditor(TerminTyp.STANDESAMT, null) }
        binding.buttonAddKirche.setOnClickListener { zeigeEditor(TerminTyp.KIRCHE, null) }
        binding.buttonAddLocation.setOnClickListener { zeigeEditor(TerminTyp.LOCATION, null) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                terminRepository.observeTermine().collect { termine -> render(termine) }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setUpSection(recycler: RecyclerView, adapter: TerminAdapter, titleView: TextView, typ: TerminTyp) {
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false
        titleView.text = getString(typ.label)
    }

    private fun render(termine: List<TerminOption>) {
        val sortiert = termine.sortedBy { it.datum }
        renderSection(TerminTyp.STANDESAMT, sortiert, adapterStandesamt, binding.recyclerStandesamt, binding.textEmptyStandesamt)
        renderSection(TerminTyp.KIRCHE, sortiert, adapterKirche, binding.recyclerKirche, binding.textEmptyKirche)
        renderSection(TerminTyp.LOCATION, sortiert, adapterLocation, binding.recyclerLocation, binding.textEmptyLocation)
    }

    private fun renderSection(
        typ: TerminTyp,
        alle: List<TerminOption>,
        adapter: TerminAdapter,
        recycler: RecyclerView,
        emptyView: TextView,
    ) {
        val gefiltert = alle.filter { it.terminTyp == typ }
        adapter.submitList(gefiltert)
        recycler.isVisible = gefiltert.isNotEmpty()
        emptyView.isVisible = gefiltert.isEmpty()
    }

    private fun zeigeEditor(typ: TerminTyp, bestehend: TerminOption?) {
        val uid = authRepository.currentUser?.uid ?: return
        val dialogBinding = DialogTerminEditBinding.inflate(layoutInflater)
        var ausgewaehltesDatum = bestehend?.datum ?: LocalDate.now()

        fun aktualisiereDatumsAnzeige() {
            dialogBinding.buttonPickDate.text = ausgewaehltesDatum.format(titelFormat).replaceFirstChar { it.uppercase() }
        }
        aktualisiereDatumsAnzeige()

        dialogBinding.buttonPickDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    ausgewaehltesDatum = LocalDate.of(year, month + 1, dayOfMonth)
                    aktualisiereDatumsAnzeige()
                },
                ausgewaehltesDatum.year,
                ausgewaehltesDatum.monthValue - 1,
                ausgewaehltesDatum.dayOfMonth,
            ).show()
        }

        dialogBinding.toggleStatus.check(buttonIdForStatus(dialogBinding, bestehend?.status ?: TerminStatus.VORGESCHLAGEN))
        dialogBinding.inputNotiz.setText(bestehend?.notiz)
        bestehend?.kostenEuro?.let { dialogBinding.inputKosten.setText(formatDouble(it)) }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.hochzeit_termin_dialog_title, getString(typ.label)))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val status = statusForButtonId(dialogBinding, dialogBinding.toggleStatus.checkedButtonId)
                    ?: TerminStatus.VORGESCHLAGEN
                val termin = (bestehend ?: TerminOption(terminTyp = typ)).copy(
                    uid = uid,
                    terminTyp = typ,
                    datum = ausgewaehltesDatum,
                    status = status,
                    notiz = dialogBinding.inputNotiz.text?.toString()?.trim().orEmpty(),
                    kostenEuro = parseDouble(dialogBinding.inputKosten.text),
                )
                lifecycleScope.launch { terminRepository.save(termin) }
            }
            .setNegativeButton(R.string.action_cancel, null)

        if (bestehend != null) {
            builder.setNeutralButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch { terminRepository.delete(bestehend.id) }
            }
        }
        builder.show()
    }

    private fun buttonIdForStatus(dialogBinding: DialogTerminEditBinding, status: TerminStatus): Int = when (status) {
        TerminStatus.VORGESCHLAGEN -> dialogBinding.buttonStatusVorgeschlagen.id
        TerminStatus.BESTAETIGT -> dialogBinding.buttonStatusBestaetigt.id
        TerminStatus.ABGESAGT -> dialogBinding.buttonStatusAbgesagt.id
    }

    private fun statusForButtonId(dialogBinding: DialogTerminEditBinding, buttonId: Int): TerminStatus? = when (buttonId) {
        dialogBinding.buttonStatusVorgeschlagen.id -> TerminStatus.VORGESCHLAGEN
        dialogBinding.buttonStatusBestaetigt.id -> TerminStatus.BESTAETIGT
        dialogBinding.buttonStatusAbgesagt.id -> TerminStatus.ABGESAGT
        else -> null
    }

    private fun formatDouble(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.GERMANY, "%.2f", value)

    private fun parseDouble(text: CharSequence?): Double? = text?.toString()?.replace(',', '.')?.toDoubleOrNull()

    companion object {
        fun intent(context: Context): Intent = Intent(context, TerminUebersichtActivity::class.java)
    }
}
