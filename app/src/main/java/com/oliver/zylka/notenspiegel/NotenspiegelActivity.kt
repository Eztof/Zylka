package com.oliver.zylka.notenspiegel

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.notenspiegel.GradingSystem
import com.oliver.zylka.data.notenspiegel.NotenspiegelCalculator
import com.oliver.zylka.data.notenspiegel.NotenspiegelSettings
import com.oliver.zylka.data.notenspiegel.NotenspiegelSettingsRepository
import com.oliver.zylka.databinding.ActivityNotenspiegelBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/**
 * Wandelt eine Gesamtpunktzahl anhand einstellbarer Prozent-Schwellen ([NotenspiegelSettings])
 * in Punkte-Bereiche je Note um, für die Sechs-Stufen- und die 15-Punkte-Notenskala.
 */
class NotenspiegelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotenspiegelBinding
    private val authRepository = AuthRepository()
    private val settingsRepository = NotenspiegelSettingsRepository()
    private val adapter = GradeBandAdapter()

    private var settings = NotenspiegelSettings()
    private var selectedSystem = GradingSystem.SECHS_STUFEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotenspiegelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerBands.layoutManager = LinearLayoutManager(this)
        binding.recyclerBands.adapter = adapter

        binding.inputTotalPoints.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = recompute()
        })

        binding.toggleSystem.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedSystem = if (checkedId == binding.buttonSystemFuenfzehn.id) {
                GradingSystem.FUNFZEHN_PUNKTE
            } else {
                GradingSystem.SECHS_STUFEN
            }
            recompute()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_notenspiegel, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, NotenspiegelSettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadSettings() {
        val uid = authRepository.currentUser?.uid ?: return
        lifecycleScope.launch {
            settings = settingsRepository.load(uid)
            recompute()
        }
    }

    private fun recompute() {
        val totalPoints = binding.inputTotalPoints.text?.toString()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        if (totalPoints == null || totalPoints <= 0.0) {
            adapter.submitList(emptyList())
            return
        }
        val thresholds = settings.thresholdsFor(selectedSystem)
        adapter.submitList(
            NotenspiegelCalculator.compute(selectedSystem, thresholds, totalPoints, settings.pointsPrecision),
        )
    }
}
