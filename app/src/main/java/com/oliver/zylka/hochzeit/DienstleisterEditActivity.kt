package com.oliver.zylka.hochzeit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.hochzeit.Dienstleister
import com.oliver.zylka.data.hochzeit.DienstleisterKategorie
import com.oliver.zylka.data.hochzeit.DienstleisterRepository
import com.oliver.zylka.data.hochzeit.DienstleisterStatus
import com.oliver.zylka.databinding.ActivityDienstleisterEditBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.util.Locale

/** Dienstleister/Kostenposten anlegen/bearbeiten: Name, Kategorie (10 Werte als ChipGroup, genau
 * eine Auswahl), Status (5 Werte als ChipGroup - für eine `MaterialButtonToggleGroup` wären das
 * zu viele, Vorbild `PlantEditActivity`s Kategorie-Chips), Kosten, Kontakt (ein Freitext-Feld für
 * Telefon/E-Mail/Website statt dreier einzelner), Notiz. */
class DienstleisterEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDienstleisterEditBinding
    private val authRepository = AuthRepository()
    private val dienstleisterRepository = DienstleisterRepository()

    private var currentDienstleister = Dienstleister()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDienstleisterEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        val dienstleisterId = intent.getStringExtra(EXTRA_DIENSTLEISTER_ID)
        if (dienstleisterId == null) {
            title = getString(R.string.hochzeit_dienstleister_edit_title_new)
            applyToForm(Dienstleister())
        } else {
            lifecycleScope.launch {
                val loaded = dienstleisterRepository.getDienstleister(dienstleisterId) ?: Dienstleister(id = dienstleisterId)
                currentDienstleister = loaded
                title = getString(R.string.hochzeit_dienstleister_edit_title_edit)
                binding.buttonDelete.isVisible = true
                applyToForm(loaded)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun applyToForm(eintrag: Dienstleister) {
        binding.inputName.setText(eintrag.name)
        binding.chipGroupKategorie.check(chipIdForKategorie(eintrag.kategorie))
        binding.chipGroupStatus.check(chipIdForStatus(eintrag.status))
        eintrag.kostenEuro?.let { binding.inputKosten.setText(formatDouble(it)) }
        binding.inputKontakt.setText(eintrag.kontakt)
        binding.inputNotiz.setText(eintrag.notiz)
    }

    private fun save() {
        val uid = authRepository.currentUser?.uid ?: return
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.hochzeit_dienstleister_edit_error_required, Toast.LENGTH_SHORT).show()
            return
        }
        val kategorie = kategorieForChipId(binding.chipGroupKategorie.checkedChipId) ?: DienstleisterKategorie.SONSTIGES
        val status = statusForChipId(binding.chipGroupStatus.checkedChipId) ?: DienstleisterStatus.IDEE

        val toSave = currentDienstleister.copy(
            uid = uid,
            name = name,
            kategorie = kategorie,
            status = status,
            kostenEuro = parseDouble(binding.inputKosten.text),
            kontakt = binding.inputKontakt.text?.toString()?.trim().orEmpty(),
            notiz = binding.inputNotiz.text?.toString()?.trim().orEmpty(),
        )
        lifecycleScope.launch {
            dienstleisterRepository.save(toSave)
            finish()
        }
    }

    private fun confirmDelete() {
        if (currentDienstleister.id.isBlank()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.hochzeit_dienstleister_edit_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    dienstleisterRepository.delete(currentDienstleister.id)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun chipIdForKategorie(kategorie: DienstleisterKategorie): Int = when (kategorie) {
        DienstleisterKategorie.DJ -> binding.chipKategorieDj.id
        DienstleisterKategorie.CATERING -> binding.chipKategorieCatering.id
        DienstleisterKategorie.FOTOGRAF -> binding.chipKategorieFotograf.id
        DienstleisterKategorie.BLUMEN -> binding.chipKategorieBlumen.id
        DienstleisterKategorie.LOCATION -> binding.chipKategorieLocation.id
        DienstleisterKategorie.TORTE -> binding.chipKategorieTorte.id
        DienstleisterKategorie.DEKORATION -> binding.chipKategorieDekoration.id
        DienstleisterKategorie.TRANSPORT -> binding.chipKategorieTransport.id
        DienstleisterKategorie.BRAUTMODE -> binding.chipKategorieBrautmode.id
        DienstleisterKategorie.SONSTIGES -> binding.chipKategorieSonstiges.id
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

    private fun chipIdForStatus(status: DienstleisterStatus): Int = when (status) {
        DienstleisterStatus.IDEE -> binding.chipStatusIdee.id
        DienstleisterStatus.ANGEFRAGT -> binding.chipStatusAngefragt.id
        DienstleisterStatus.ANGEBOT_ERHALTEN -> binding.chipStatusAngebotErhalten.id
        DienstleisterStatus.GEBUCHT -> binding.chipStatusGebucht.id
        DienstleisterStatus.ABGESAGT -> binding.chipStatusAbgesagt.id
    }

    private fun statusForChipId(chipId: Int): DienstleisterStatus? = when (chipId) {
        binding.chipStatusIdee.id -> DienstleisterStatus.IDEE
        binding.chipStatusAngefragt.id -> DienstleisterStatus.ANGEFRAGT
        binding.chipStatusAngebotErhalten.id -> DienstleisterStatus.ANGEBOT_ERHALTEN
        binding.chipStatusGebucht.id -> DienstleisterStatus.GEBUCHT
        binding.chipStatusAbgesagt.id -> DienstleisterStatus.ABGESAGT
        else -> null
    }

    private fun formatDouble(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.GERMANY, "%.2f", value)

    private fun parseDouble(text: CharSequence?): Double? = text?.toString()?.replace(',', '.')?.toDoubleOrNull()

    companion object {
        private const val EXTRA_DIENSTLEISTER_ID = "dienstleister_id"

        fun intent(context: Context, dienstleisterId: String? = null): Intent =
            Intent(context, DienstleisterEditActivity::class.java).apply {
                if (dienstleisterId != null) putExtra(EXTRA_DIENSTLEISTER_ID, dienstleisterId)
            }
    }
}
