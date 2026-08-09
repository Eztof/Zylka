package com.oliver.zylka.plants

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.Plant
import com.oliver.zylka.data.plants.PlantCategory
import com.oliver.zylka.data.plants.PlantRepository
import com.oliver.zylka.databinding.ActivityPlantEditBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Pflanze anlegen/bearbeiten: Name, Kategorie (belegt den Verdunstungsfaktor `kcBasis` vor,
 * frei überschreibbar), Größenfaktor, Anzahl (mehrere gleiche Pflanzen als ein Eintrag). */
class PlantEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlantEditBinding
    private val authRepository = AuthRepository()
    private val plantRepository = PlantRepository()

    private lateinit var potId: String
    private var plant = Plant()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlantEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        potId = intent.getStringExtra(EXTRA_POT_ID) ?: run { finish(); return }
        val plantId = intent.getStringExtra(EXTRA_PLANT_ID)

        setUpKategorieChips()
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        if (plantId == null) {
            title = getString(R.string.plant_edit_title_new)
            applyPlantToForm(Plant(potId = potId))
        } else {
            binding.buttonDelete.isVisible = true
            lifecycleScope.launch {
                val loaded = plantRepository.getPlant(plantId) ?: Plant(id = plantId, potId = potId)
                plant = loaded
                title = getString(R.string.plant_edit_title_edit)
                applyPlantToForm(loaded)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setUpKategorieChips() {
        binding.chipGroupKategorie.setOnCheckedStateChangeListener { _, checkedIds ->
            val kategorie = kategorieForChipId(checkedIds.firstOrNull()) ?: return@setOnCheckedStateChangeListener
            binding.inputKcBasis.setText(formatDouble(kategorie.kcBasisDefault))
        }
    }

    private fun applyPlantToForm(p: Plant) {
        binding.inputName.setText(p.name)
        chipFor(p.kategorie).isChecked = true
        binding.inputKcBasis.setText(formatDouble(p.kcBasis))
        binding.inputGroessenfaktor.setText(formatDouble(p.groessenfaktor))
        binding.inputAnzahl.setText(p.anzahl.toString())
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.plant_edit_error_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        val kategorie = kategorieForChipId(binding.chipGroupKategorie.checkedChipId) ?: PlantCategory.STANDARD
        val kcBasis = binding.inputKcBasis.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            ?: kategorie.kcBasisDefault
        val groessenfaktor = binding.inputGroessenfaktor.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 1.0
        val anzahl = binding.inputAnzahl.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val uid = authRepository.currentUser?.uid ?: return

        val toSave = plant.copy(
            uid = uid,
            potId = potId,
            name = name,
            kategorie = kategorie,
            kcBasis = kcBasis,
            groessenfaktor = groessenfaktor,
            anzahl = anzahl,
        )
        lifecycleScope.launch {
            plantRepository.save(toSave)
            finish()
        }
    }

    private fun confirmDelete() {
        if (plant.id.isBlank()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.plant_edit_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    plantRepository.delete(plant.id)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun chipFor(kategorie: PlantCategory): Chip = when (kategorie) {
        PlantCategory.SUKKULENTE -> binding.chipKategorieSukkulente
        PlantCategory.MEDITERRAN -> binding.chipKategorieMediterran
        PlantCategory.STANDARD -> binding.chipKategorieStandard
        PlantCategory.DURSTIG -> binding.chipKategorieDurstig
        PlantCategory.GEMUESE -> binding.chipKategorieGemuese
    }

    private fun kategorieForChipId(chipId: Int?): PlantCategory? = when (chipId) {
        binding.chipKategorieSukkulente.id -> PlantCategory.SUKKULENTE
        binding.chipKategorieMediterran.id -> PlantCategory.MEDITERRAN
        binding.chipKategorieStandard.id -> PlantCategory.STANDARD
        binding.chipKategorieDurstig.id -> PlantCategory.DURSTIG
        binding.chipKategorieGemuese.id -> PlantCategory.GEMUESE
        else -> null
    }

    private fun formatDouble(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    companion object {
        private const val EXTRA_POT_ID = "pot_id"
        private const val EXTRA_PLANT_ID = "plant_id"

        fun intent(context: Context, potId: String, plantId: String?): Intent =
            Intent(context, PlantEditActivity::class.java)
                .putExtra(EXTRA_POT_ID, potId)
                .putExtra(EXTRA_PLANT_ID, plantId)
    }
}
