package com.oliver.zylka.plants

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.plants.PlantForecastRepository
import com.oliver.zylka.data.plants.PlantPrefs
import com.oliver.zylka.data.plants.PotForecast

/**
 * Plant Gieß-Erinnerungen als Kette einzelner exakter Alarme - exakt wie `WasteAlarmScheduler`:
 * es ist immer nur ein Alarm gleichzeitig geplant, für den dringlichsten Topf über
 * [PlantForecastRepository]. Der Alarm feuert genau dann, wenn die Prognose den in
 * [PlantPrefs.schwellenwertProzent] eingestellten Feuchte-Schwellenwert unterschreitet - keine
 * feste Uhrzeit. Löst er aus, zeigt [PlantAlarmReceiver] die Benachrichtigung und plant im
 * selben Schritt sofort den nächsten. Muss nach jeder Neuberechnung der Prognose (Wetter-
 * Refresh, Gießen, Schwellenwert geändert) erneut aufgerufen werden.
 */
object PlantAlarmScheduler {

    private const val REQUEST_CODE = 4301

    /** Berechnet die Prognosen selbst (für Aufrufer ohne bereits vorliegende Liste - Alarm-/
     * Boot-Receiver, Erinnerung an/aus, Schwellenwert geändert). Liegt schon eine frisch
     * berechnete Liste vor (z. B. `PlantsHomeActivity` nach dem Laden für die Anzeige),
     * stattdessen direkt [scheduleFor] aufrufen - spart eine komplette zweite Berechnung
     * (Wetterabruf, Standortabfrage, Firestore-Lesezugriffe je Topf). */
    suspend fun rescheduleNext(context: Context) {
        val prefs = PlantPrefs(context)
        val uid = AuthRepository().currentUser?.uid
        if (!prefs.reminderEnabled || uid == null) {
            cancel(context)
            return
        }
        val schwelleAnteil = prefs.schwellenwertProzent / 100.0
        val forecasts = runCatching { PlantForecastRepository(context).computeForecasts(uid, schwelleAnteil) }
            .getOrNull().orEmpty()
        scheduleFor(context, forecasts)
    }

    /** Plant den Alarm anhand einer bereits vorliegenden Prognose-Liste, ohne sie neu zu
     * berechnen - siehe [rescheduleNext]. */
    fun scheduleFor(context: Context, forecasts: List<PotForecast>) {
        val prefs = PlantPrefs(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!prefs.reminderEnabled) {
            alarmManager.cancel(buildPendingIntent(context))
            return
        }

        val next = forecasts.firstOrNull { it.faelligAbEpochMillis != null }
        if (next == null) {
            alarmManager.cancel(buildPendingIntent(context))
            return
        }

        // Liegt die Fälligkeit schon in der Vergangenheit (Topf bereits überfällig), sofort
        // benachrichtigen statt zu warten.
        val triggerMillis = maxOf(next.faelligAbEpochMillis!!, System.currentTimeMillis())
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
