package com.oliver.zylka.waste

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oliver.zylka.data.waste.WasteCalendarRepository
import com.oliver.zylka.data.waste.WasteEvent
import com.oliver.zylka.data.waste.WastePrefs
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules waste-pickup reminders as a chain of single exact alarms: only ever one alarm
 * is pending at a time. When it fires, [WasteAlarmReceiver] shows the notification and
 * immediately re-arms the alarm for the next upcoming event. This keeps working even if
 * the app process is closed (AlarmManager + a manifest-registered BroadcastReceiver) and
 * across reboots ([WasteBootReceiver] re-arms the chain, since exact alarms don't survive
 * a restart).
 */
object WasteAlarmScheduler {

    private const val REQUEST_CODE = 4201

    suspend fun rescheduleNext(context: Context) {
        val prefs = WastePrefs(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!prefs.reminderEnabled) {
            alarmManager.cancel(buildPendingIntent(context))
            return
        }

        val repository = WasteCalendarRepository(context)
        val events = repository.loadEvents()
        val now = LocalDateTime.now()
        val next = events.firstOrNull { triggerTime(it, prefs).isAfter(now) }

        if (next == null) {
            alarmManager.cancel(buildPendingIntent(context))
            return
        }

        val triggerMillis = triggerTime(next, prefs)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val pendingIntent = buildPendingIntent(context, next)

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            // Ohne die Berechtigung für exakte Alarme lieber ungefähr erinnern als gar
            // nicht - das System kann die Zustellung dann etwas verzögern.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun triggerTime(event: WasteEvent, prefs: WastePrefs): LocalDateTime =
        event.date.minusDays(1).atTime(prefs.reminderHour, prefs.reminderMinute)

    private fun buildPendingIntent(context: Context, event: WasteEvent? = null): PendingIntent {
        val intent = Intent(context, WasteAlarmReceiver::class.java)
        if (event != null) {
            intent.putExtra(WasteAlarmReceiver.EXTRA_DATE, event.date.toString())
            intent.putExtra(WasteAlarmReceiver.EXTRA_TYPES, event.types.map { it.id }.toTypedArray())
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
