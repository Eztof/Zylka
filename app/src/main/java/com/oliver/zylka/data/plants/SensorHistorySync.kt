package com.oliver.zylka.data.plants

import android.content.Context
import java.util.Date

/** Gleicht die im Gerät gespeicherte Historie höchstens einmal pro Tag und Sensor mit Firestore
 * ab (siehe [SensorHistorySyncPrefs]) - verhindert Anfrage-/Schreib-Spam, wenn die App mehrmals
 * täglich geöffnet wird. Wird still im Hintergrund von `MainActivity` angestoßen; Fehler (Gerät
 * nicht erreichbar, Bluetooth aus, ...) werden pro Sensor verschluckt, ohne die übrigen Sensoren
 * zu blockieren. */
class SensorHistorySync(
    context: Context,
    private val sensorRepository: SensorRepository = SensorRepository(),
    private val sensorReadingRepository: SensorReadingRepository = SensorReadingRepository(),
    private val bleScanner: SensorBleScanner = SensorBleScanner(context.applicationContext),
) {

    private val prefs = SensorHistorySyncPrefs(context)

    suspend fun syncDueSensors(uid: String) {
        if (!bleScanner.hasPermissions() || !bleScanner.isBluetoothEnabled()) return
        for (sensor in sensorRepository.loadSensors()) {
            if (sensor.macAddress.isBlank()) continue
            if (System.currentTimeMillis() - prefs.lastSyncMillis(sensor.id) < SYNC_INTERVAL_MILLIS) continue
            runCatching { syncSensor(uid, sensor) }
        }
    }

    /** Lädt die Gerätehistorie eines Sensors und ergänzt in Firestore nur Datensätze, die neuer
     * als der bereits gespeicherte letzte Messwert sind (Duplikat-/Spam-Schutz aus der
     * Nutzeranforderung). Der Sync-Zeitpunkt wird unabhängig vom Ergebnis vermerkt, damit ein
     * dauerhaft nicht erreichbarer Sensor nicht bei jedem App-Start erneut versucht wird. */
    private suspend fun syncSensor(uid: String, sensor: Sensor) {
        try {
            val history = bleScanner.readHistory(sensor.macAddress) ?: return
            if (history.isNotEmpty()) {
                val latestExisting = sensorReadingRepository.loadReadings(sensor.id)
                    .maxOfOrNull { it.measuredAt?.time ?: 0L } ?: 0L
                val intervalMillis = SensorBleScanner.HISTORY_RECORD_INTERVAL_MINUTES * 60_000L
                val now = System.currentTimeMillis()
                val newPoints = history.mapNotNull { reading ->
                    val measuredAt = now - reading.index * intervalMillis
                    if (measuredAt <= latestExisting) {
                        null
                    } else {
                        HistoricalPoint(Date(measuredAt), reading.temperatureC, reading.humidityPercent)
                    }
                }
                if (newPoints.isNotEmpty()) {
                    sensorReadingRepository.recordHistoricalReadings(uid, sensor.id, newPoints)
                }
            }
        } finally {
            prefs.markSynced(sensor.id)
        }
    }

    companion object {
        private const val SYNC_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
    }
}
