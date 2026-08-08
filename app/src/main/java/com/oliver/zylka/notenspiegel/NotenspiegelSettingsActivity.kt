package com.oliver.zylka.notenspiegel

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.notenspiegel.GradingPresets
import com.oliver.zylka.data.notenspiegel.GradingSystem
import com.oliver.zylka.data.notenspiegel.NotenspiegelSettings
import com.oliver.zylka.data.notenspiegel.NotenspiegelSettingsRepository
import com.oliver.zylka.databinding.ActivityNotenspiegelSettingsBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Lässt die Prozent-Schwellen beider Notensysteme bearbeiten und online speichern. */
class NotenspiegelSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotenspiegelSettingsBinding
    private val authRepository = AuthRepository()
    private val settingsRepository = NotenspiegelSettingsRepository()
    private val adapter = GradeThresholdEditAdapter()

    private var settings = NotenspiegelSettings()
    private var selectedSystem = GradingSystem.SECHS_STUFEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotenspiegelSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerThresholds.layoutManager = LinearLayoutManager(this)
        binding.recyclerThresholds.adapter = adapter

        binding.toggleSystem.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            storeEditsIntoSettings()
            selectedSystem = if (checkedId == binding.buttonSystemFuenfzehn.id) {
                GradingSystem.FUNFZEHN_PUNKTE
            } else {
                GradingSystem.SECHS_STUFEN
            }
            showThresholdsForSelectedSystem()
        }

        binding.buttonReset.setOnClickListener {
            settings = settings.withThresholds(selectedSystem, GradingPresets.defaultFor(selectedSystem))
            showThresholdsForSelectedSystem()
        }

        binding.buttonSave.setOnClickListener { save() }

        lifecycleScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            settings = settingsRepository.load(uid)
            showThresholdsForSelectedSystem()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showThresholdsForSelectedSystem() {
        adapter.submit(selectedSystem.grades, settings.thresholdsFor(selectedSystem))
    }

    private fun storeEditsIntoSettings() {
        settings = settings.withThresholds(selectedSystem, adapter.currentValues())
    }

    private fun save() {
        storeEditsIntoSettings()
        val uid = authRepository.currentUser?.uid ?: return
        lifecycleScope.launch {
            settingsRepository.save(uid, settings)
            Toast.makeText(this@NotenspiegelSettingsActivity, R.string.notenspiegel_settings_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
