package com.oliver.zylka.plants

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.PlantForecastRepository
import com.oliver.zylka.data.plants.PlantPrefs
import com.oliver.zylka.data.plants.PotForecast
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Plant Gieß-Erinnerungen als Kette einzelner exakter Alarme - exakt wie `WasteAlarmScheduler`:
 * es ist immer nur ein Alarm gleichzeitig geplant, für den dringlichsten Topf über
 * [PlantForecastRepository]. Löst er aus, zeigt [PlantAlarmReceiver] die Benachrichtigung und
 * plant im selben Schritt sofort den nächsten. Muss nach jeder Neuberechnung der Prognose
 * (Wetter-Refresh, Gießen, Reminder-Einstellung geändert) erneut aufgerufen werden.
 */
object PlantAlarmScheduler {

    private const val REQUEST_CODE = 4301

    suspend fun rescheduleNext(context: Context) {
        val prefs = PlantPrefs(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val uid = AuthRepository().currentUser?.uid
        if (!prefs.reminderEnabled || uid == null) {
            alarmManager.cancel(buildPendingIntent(context))
            return
        }

        val forecasts = runCatching { PlantForecastRepository(context).computeForecasts(uid) }
            .getOrNull().orEmpty()
        val next = forecasts.firstOrNull { it.faelligAbEpochMillis != null }

        if (next == null) {
            alarmManager.cancel(buildPendingIntent(context))
            return
        }

        val triggerMillis = triggerTime(next, prefs)
        val pendingIntent = buildPendingIntent(context, next.pot.name)

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

    /**
     * Alarmzeit = Reminder-Uhrzeit am Fälligkeitstag. Liegt das schon in der Vergangenheit
     * (der Topf ist bereits überfällig und die heutige Reminder-Uhrzeit schon vorbei), wird
     * auf die nächste Vorkommen der Reminder-Uhrzeit gelegt (heute, falls noch nicht vorbei,
     * sonst morgen) - "Jetzt gießen" bleibt so nicht unbeachtet.
     */
    private fun triggerTime(forecast: PotForecast, prefs: PlantPrefs): Long {
        val zone = ZoneId.systemDefault()
        val dueDate = Instant.ofEpochMilli(forecast.faelligAbEpochMillis!!).atZone(zone).toLocalDate()
        val now = LocalDateTime.now(zone)
        var trigger = dueDate.atTime(prefs.reminderHour, prefs.reminderMinute)
        if (trigger.isBefore(now)) {
            trigger = now.toLocalDate().atTime(prefs.reminderHour, prefs.reminderMinute)
            if (trigger.isBefore(now)) {
                trigger = trigger.plusDays(1)
            }
        }
        return trigger.atZone(zone).toInstant().toEpochMilli()
    }

    private fun buildPendingIntent(context: Context, potName: String? = null): PendingIntent {
        val intent = Intent(context, PlantAlarmReceiver::class.java)
        if (potName != null) {
            intent.putExtra(PlantAlarmReceiver.EXTRA_POT_NAME, potName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
