package com.oliver.zylka.plants

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.PlantForecastRepository
import com.oliver.zylka.data.plants.PlantPrefs
import com.oliver.zylka.data.plants.PlantWaterCalculator
import com.oliver.zylka.data.plants.PotForecast
import com.oliver.zylka.data.plants.PotRepository
import com.oliver.zylka.data.plants.WateringFeedback
import com.oliver.zylka.data.plants.WateringRepository
import com.oliver.zylka.databinding.ActivityPlantsHomeBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/**
 * Übersicht aller Töpfe, sortiert nach Dringlichkeit ([PlantForecastRepository]): Fortschritt,
 * "Gießen in X Tagen" bzw. "Jetzt gießen", Button "Gegossen". Erinnerung wie beim
 * Abfallkalender direkt auf diesem Screen (Switch + Uhrzeit), Vorbild `WasteCalendarActivity`.
 */
class PlantsHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlantsHomeBinding
    private lateinit var prefs: PlantPrefs
    private val authRepository = AuthRepository()
    private val potRepository = PotRepository()
    private val wateringRepository = WateringRepository()
    private val forecastRepository by lazy { PlantForecastRepository(applicationContext) }
    private val adapter = PotSummaryAdapter(
        onWatered = { forecast -> onWatered(forecast) },
        onOpenDetail = { forecast -> startActivity(PotDetailActivity.intent(this, forecast.pot.id)) },
    )

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            applyReminderEnabled(true)
        } else {
            binding.switchReminder.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlantsHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = PlantPrefs(this)

        binding.recyclerPots.layoutManager = LinearLayoutManager(this)
        binding.recyclerPots.adapter = adapter

        binding.switchReminder.setOnCheckedChangeListener(null)
        binding.switchReminder.isChecked = prefs.reminderEnabled
        updateReminderTimeLabel()

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestReminderEnable() else applyReminderEnabled(false)
        }
        binding.textReminderTime.setOnClickListener { showTimePicker() }
    }

    override fun onResume() {
        super.onResume()
        loadForecasts()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_plants_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_pot) {
            startActivity(Intent(this, PotEditActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadForecasts() {
        val uid = authRepository.currentUser?.uid ?: return
        binding.progressLoading.isVisible = true
        binding.textEmpty.isVisible = false
        lifecycleScope.launch {
            val forecasts = forecastRepository.computeForecasts(uid)
            binding.progressLoading.isVisible = false
            binding.textEmpty.isVisible = forecasts.isEmpty()
            adapter.submitList(forecasts)
            rescheduleAlarm()
        }
    }

    private fun onWatered(forecast: PotForecast) {
        val options = WateringFeedback.entries.map { getString(it.label) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.watering_feedback_title, forecast.pot.name))
            .setItems(options) { _, which -> applyWatering(forecast, WateringFeedback.entries[which]) }
            .setNegativeButton(R.string.watering_feedback_skip) { _, _ -> applyWatering(forecast, null) }
            .show()
    }

    private fun applyWatering(forecast: PotForecast, feedback: WateringFeedback?) {
        val uid = authRepository.currentUser?.uid ?: return
        lifecycleScope.launch {
            wateringRepository.recordWatering(uid, forecast.pot.id, feedback)

            val verbrauchtBisGiessenMm = forecast.pot.kapazitaetMm - forecast.vorratJetztMm
            val neueKapazitaet = PlantWaterCalculator.recalibrateCapacity(
                kapazitaetMm = forecast.pot.kapazitaetMm,
                kapazitaetStartwertMm = forecast.pot.kapazitaetStartwertMm,
                verbrauchtBisGiessenMm = verbrauchtBisGiessenMm,
                feedback = feedback,
            )
            if (neueKapazitaet != forecast.pot.kapazitaetMm) {
                potRepository.save(forecast.pot.copy(kapazitaetMm = neueKapazitaet))
            }
            loadForecasts()
        }
    }

    private fun requestReminderEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        applyReminderEnabled(true)
    }

    private fun applyReminderEnabled(enabled: Boolean) {
        prefs.reminderEnabled = enabled
        updateReminderTimeLabel()
        rescheduleAlarm()
        if (enabled) maybeRequestExactAlarmAccess()
    }

    private fun maybeRequestExactAlarmAccess() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                prefs.setReminderTime(hour, minute)
                updateReminderTimeLabel()
                rescheduleAlarm()
            },
            prefs.reminderHour,
            prefs.reminderMinute,
            true,
        ).show()
    }

    private fun updateReminderTimeLabel() {
        binding.textReminderTime.isVisible = prefs.reminderEnabled
        val time = "%02d:%02d".format(prefs.reminderHour, prefs.reminderMinute)
        binding.textReminderTime.text = getString(R.string.plants_reminder_time, time)
    }

    private fun rescheduleAlarm() {
        lifecycleScope.launch { PlantAlarmScheduler.rescheduleNext(applicationContext) }
    }
}
