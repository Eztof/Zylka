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
import com.oliver.zylka.data.hochzeit.Gast
import com.oliver.zylka.data.hochzeit.GastKategorie
import com.oliver.zylka.data.hochzeit.GastPrioritaet
import com.oliver.zylka.data.hochzeit.GastRepository
import com.oliver.zylka.databinding.ActivityGastEditBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Gast anlegen/bearbeiten: Name, Kategorie (Chips, genau eine Auswahl - Vorbild
 * `PlantEditActivity`s Kategorie-Chips), Priorität (3-Wege-Toggle - Vorbild `PotEditActivity`s
 * `toggle_standort`), Notiz. */
class GastEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGastEditBinding
    private val authRepository = AuthRepository()
    private val gastRepository = GastRepository()

    private var currentGast = Gast()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGastEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        val gastId = intent.getStringExtra(EXTRA_GAST_ID)
        if (gastId == null) {
            title = getString(R.string.hochzeit_gast_edit_title_new)
            applyGastToForm(Gast())
        } else {
            lifecycleScope.launch {
                val loaded = gastRepository.getGast(gastId) ?: Gast(id = gastId)
                currentGast = loaded
                title = getString(R.string.hochzeit_gast_edit_title_edit)
                binding.buttonDelete.isVisible = true
                applyGastToForm(loaded)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun applyGastToForm(gast: Gast) {
        binding.inputName.setText(gast.name)
        binding.chipGroupKategorie.check(chipIdForKategorie(gast.kategorie))
        binding.togglePrioritaet.check(buttonIdForPrioritaet(gast.prioritaet))
        binding.inputNotiz.setText(gast.notiz)
    }

    private fun save() {
        val uid = authRepository.currentUser?.uid ?: return
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.hochzeit_gast_edit_error_required, Toast.LENGTH_SHORT).show()
            return
        }
        val kategorie = kategorieForChipId(binding.chipGroupKategorie.checkedChipId) ?: GastKategorie.FREUNDE
        val prioritaet = prioritaetForButtonId(binding.togglePrioritaet.checkedButtonId) ?: GastPrioritaet.MITTEL

        val toSave = currentGast.copy(
            uid = uid,
            name = name,
            kategorie = kategorie,
            prioritaet = prioritaet,
            notiz = binding.inputNotiz.text?.toString()?.trim().orEmpty(),
        )
        lifecycleScope.launch {
            gastRepository.save(toSave)
            finish()
        }
    }

    private fun confirmDelete() {
        if (currentGast.id.isBlank()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.hochzeit_gast_edit_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    gastRepository.delete(currentGast.id)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun chipIdForKategorie(kategorie: GastKategorie): Int = when (kategorie) {
        GastKategorie.FREUNDE -> binding.chipKategorieFreunde.id
        GastKategorie.FAMILIE -> binding.chipKategorieFamilie.id
        GastKategorie.ARBEITSKOLLEGEN -> binding.chipKategorieArbeitskollegen.id
    }

    private fun kategorieForChipId(chipId: Int): GastKategorie? = when (chipId) {
        binding.chipKategorieFreunde.id -> GastKategorie.FREUNDE
        binding.chipKategorieFamilie.id -> GastKategorie.FAMILIE
        binding.chipKategorieArbeitskollegen.id -> GastKategorie.ARBEITSKOLLEGEN
        else -> null
    }

    private fun buttonIdForPrioritaet(prioritaet: GastPrioritaet): Int = when (prioritaet) {
        GastPrioritaet.SICHER -> binding.buttonPrioritaetSicher.id
        GastPrioritaet.MITTEL -> binding.buttonPrioritaetMittel.id
        GastPrioritaet.MAEH -> binding.buttonPrioritaetMaeh.id
    }

    private fun prioritaetForButtonId(buttonId: Int): GastPrioritaet? = when (buttonId) {
        binding.buttonPrioritaetSicher.id -> GastPrioritaet.SICHER
        binding.buttonPrioritaetMittel.id -> GastPrioritaet.MITTEL
        binding.buttonPrioritaetMaeh.id -> GastPrioritaet.MAEH
        else -> null
    }

    companion object {
        private const val EXTRA_GAST_ID = "gast_id"

        fun intent(context: Context, gastId: String? = null): Intent =
            Intent(context, GastEditActivity::class.java).apply {
                if (gastId != null) putExtra(EXTRA_GAST_ID, gastId)
            }
    }
}
