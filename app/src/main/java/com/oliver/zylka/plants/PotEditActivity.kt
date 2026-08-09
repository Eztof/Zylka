package com.oliver.zylka.plants

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.kennzeichen.LocationHelper
import com.oliver.zylka.data.plants.Plant
import com.oliver.zylka.data.plants.PlantRepository
import com.oliver.zylka.data.plants.PlantWaterCalculator
import com.oliver.zylka.data.plants.Pot
import com.oliver.zylka.data.plants.PotRepository
import com.oliver.zylka.data.plants.Standort
import com.oliver.zylka.databinding.ActivityPotEditBinding
import com.oliver.zylka.databinding.ItemPlantMiniBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Topf anlegen/bearbeiten: Name, Durchmesser (mit live berechneter Kapazitäts-Vorschau,
 * [PlantWaterCalculator.startKapazitaet]), Standort, Standortfaktor (Startwert aus dem
 * Standort, danach frei nachjustierbar - kalibriert sich nicht automatisch mit), optionale
 * Position, zugeordnete Pflanzen. Ein neuer Topf wird beim ersten "+ Pflanze hinzufügen"
 * automatisch gespeichert, damit eine `potId` für die Zuordnung existiert.
 */
class PotEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPotEditBinding
    private val authRepository = AuthRepository()
    private val potRepository = PotRepository()
    private val plantRepository = PlantRepository()
    private val locationHelper by lazy { LocationHelper(applicationContext) }

    private var currentPot = Pot()
    private var assignedPlants: List<Plant> = emptyList()

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) useCurrentLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPotEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.inputDurchmesser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateCapacityPreview()
        })
        binding.toggleStandort.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val standort = standortForButtonId(checkedId) ?: return@addOnButtonCheckedListener
            binding.inputStandortfaktor.setText(formatDouble(standort.standortfaktorDefault))
        }
        binding.buttonUseLocation.setOnClickListener { onUseLocationClicked() }
        binding.buttonAddPlant.setOnClickListener { onAddPlantClicked() }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        val potId = intent.getStringExtra(EXTRA_POT_ID)
        if (potId == null) {
            title = getString(R.string.pot_edit_title_new)
            applyPotToForm(Pot())
            renderPlantsList()
        } else {
            lifecycleScope.launch {
                val loaded = potRepository.getPot(potId) ?: Pot(id = potId)
                currentPot = loaded
                title = getString(R.string.pot_edit_title_edit)
                binding.buttonDelete.isVisible = true
                applyPotToForm(loaded)
                loadAssignedPlants()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentPot.id.isNotBlank()) loadAssignedPlants()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun applyPotToForm(pot: Pot) {
        binding.inputName.setText(pot.name)
        if (pot.durchmesserCm > 0.0) binding.inputDurchmesser.setText(formatDouble(pot.durchmesserCm))
        binding.toggleStandort.check(buttonIdForStandort(pot.standort))
        binding.inputStandortfaktor.setText(formatDouble(pot.standortfaktor))
        pot.latitude?.let { binding.inputLatitude.setText(it.toString()) }
        pot.longitude?.let { binding.inputLongitude.setText(it.toString()) }
        updateCapacityPreview()
    }

    private fun updateCapacityPreview() {
        val durchmesser = binding.inputDurchmesser.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (durchmesser == null || durchmesser <= 0.0) {
            binding.textCapacityPreview.text = getString(R.string.pot_edit_capacity_preview_empty)
            return
        }
        val estimate = PlantWaterCalculator.startKapazitaet(durchmesser)
        binding.textCapacityPreview.text = getString(
            R.string.pot_edit_capacity_preview,
            formatDouble(estimate.volumenLiter),
            formatDouble(estimate.kapazitaetMm),
        )
    }

    private fun onUseLocationClicked() {
        if (!locationHelper.hasPermission()) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        useCurrentLocation()
    }

    private fun useCurrentLocation() {
        lifecycleScope.launch {
            val location = locationHelper.currentLocationOrNull()
            if (location == null) {
                Toast.makeText(this@PotEditActivity, R.string.pot_edit_location_unavailable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            binding.inputLatitude.setText(location.first.toString())
            binding.inputLongitude.setText(location.second.toString())
        }
    }

    private fun onAddPlantClicked() {
        lifecycleScope.launch {
            val potId = ensureSaved() ?: return@launch
            startActivity(PlantEditActivity.intent(this@PotEditActivity, potId, plantId = null))
        }
    }

    private fun loadAssignedPlants() {
        val potId = currentPot.id.takeIf { it.isNotBlank() } ?: return
        lifecycleScope.launch {
            assignedPlants = plantRepository.loadPlantsForPot(potId)
            renderPlantsList()
        }
    }

    private fun renderPlantsList() {
        binding.containerPlants.removeAllViews()
        for (plant in assignedPlants) {
            val row = ItemPlantMiniBinding.inflate(layoutInflater, binding.containerPlants, false)
            row.textPlantName.text = plant.name
            row.textPlantCategory.text = getString(plant.kategorie.label)
            row.root.setOnClickListener {
                startActivity(PlantEditActivity.intent(this, currentPot.id, plant.id))
            }
            binding.containerPlants.addView(row.root)
        }
    }

    private suspend fun ensureSaved(): String? {
        val uid = authRepository.currentUser?.uid ?: return null
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val durchmesser = binding.inputDurchmesser.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (name.isBlank() || durchmesser == null || durchmesser <= 0.0) {
            Toast.makeText(this, R.string.pot_edit_error_required, Toast.LENGTH_SHORT).show()
            return null
        }
        val standort = standortForButtonId(binding.toggleStandort.checkedButtonId) ?: Standort.FREI
        val standortfaktor = binding.inputStandortfaktor.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            ?: standort.standortfaktorDefault
        val latitude = binding.inputLatitude.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val longitude = binding.inputLongitude.text?.toString()?.replace(',', '.')?.toDoubleOrNull()

        // Durchmesser geändert (oder neuer Topf) -> Kapazität geometrisch neu ansetzen; die
        // bisherige Selbstkalibrierung basierte sonst auf der falschen Geometrie.
        val durchmesserChanged = currentPot.id.isBlank() || durchmesser != currentPot.durchmesserCm
        val estimate = PlantWaterCalculator.startKapazitaet(durchmesser)
        val kapazitaetMm = if (durchmesserChanged) estimate.kapazitaetMm else currentPot.kapazitaetMm
        val kapazitaetStartwertMm = if (durchmesserChanged) estimate.kapazitaetMm else currentPot.kapazitaetStartwertMm

        val toSave = currentPot.copy(
            uid = uid,
            name = name,
            durchmesserCm = durchmesser,
            volumenLiter = estimate.volumenLiter,
            standort = standort,
            kapazitaetMm = kapazitaetMm,
            kapazitaetStartwertMm = kapazitaetStartwertMm,
            standortfaktor = standortfaktor,
            latitude = latitude,
            longitude = longitude,
        )
        val id = potRepository.save(toSave)
        currentPot = toSave.copy(id = id)
        binding.buttonDelete.isVisible = true
        renderPlantsList()
        return id
    }

    private fun save() {
        lifecycleScope.launch {
            if (ensureSaved() != null) finish()
        }
    }

    private fun confirmDelete() {
        if (currentPot.id.isBlank()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.pot_edit_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    potRepository.delete(currentPot.id)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun buttonIdForStandort(standort: Standort): Int = when (standort) {
        Standort.FREI -> binding.buttonStandortFrei.id
        Standort.UNTER_DACH -> binding.buttonStandortUnterDach.id
        Standort.INNEN -> binding.buttonStandortInnen.id
    }

    private fun standortForButtonId(buttonId: Int): Standort? = when (buttonId) {
        binding.buttonStandortFrei.id -> Standort.FREI
        binding.buttonStandortUnterDach.id -> Standort.UNTER_DACH
        binding.buttonStandortInnen.id -> Standort.INNEN
        else -> null
    }

    private fun formatDouble(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.GERMANY, "%.1f", value)

    companion object {
        private const val EXTRA_POT_ID = "pot_id"

        fun intent(context: Context, potId: String? = null): Intent =
            Intent(context, PotEditActivity::class.java).apply {
                if (potId != null) putExtra(EXTRA_POT_ID, potId)
            }
    }
}
