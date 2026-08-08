package com.oliver.zylka.waste

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.waste.WasteCalendarRepository
import com.oliver.zylka.data.waste.WastePrefs
import com.oliver.zylka.databinding.ActivityWasteCalendarBinding
import kotlinx.coroutines.launch

/**
 * Overview of the bundled waste-collection calendar (Bünde, Langenkamp 2026/2027) with
 * an optional reminder that fires as a system notification the evening before each
 * pickup - even if the app has been fully closed since - via [WasteAlarmScheduler].
 */
class WasteCalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWasteCalendarBinding
    private lateinit var prefs: WastePrefs
    private val repository by lazy { WasteCalendarRepository(applicationContext) }
    private val adapter = WasteEventAdapter()

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
        binding = ActivityWasteCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = WastePrefs(this)

        binding.recyclerEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerEvents.adapter = adapter

        binding.switchReminder.setOnCheckedChangeListener(null)
        binding.switchReminder.isChecked = prefs.reminderEnabled
        updateReminderTimeLabel()

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestReminderEnable()
            } else {
                applyReminderEnabled(false)
            }
        }
        binding.textReminderTime.setOnClickListener { showTimePicker() }

        lifecycleScope.launch {
            adapter.submitList(repository.upcomingEvents())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
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
        binding.textReminderTime.text = getString(R.string.waste_reminder_time_evening_before, time)
    }

    private fun rescheduleAlarm() {
        lifecycleScope.launch { WasteAlarmScheduler.rescheduleNext(applicationContext) }
    }
}
