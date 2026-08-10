package com.oliver.zylka.data.plants

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Ein Messwert mit selbst gesetztem (statt `serverTimestamp()`) Zeitpunkt, für den
 * Massenimport aus [SensorBleScanner.readHistory] - siehe [SensorReadingRepository.recordHistoricalReadings]. */
data class HistoricalPoint(val measuredAt: Date, val temperatureC: Double, val humidityPercent: Double)

/**
 * Append-only Messwert-Log (`sensor_readings/{autoId}`) eines [Sensor]s, wie
 * [WateringRepository]: nie geändert oder gelöscht, nur angelegt.
 *
 * Feldnamen in Firestore sind bewusst Deutsch (`gemessenAm`/`temperaturC`/
 * `feuchtigkeitProzent`) statt der ursprünglichen englischen Namen (`measuredAt`/
 * `temperatureC`/`humidityPercent`) - gleicher Grund wie bei [SensorRepository]. `uid`/
 * `sensorId` bleiben wie in den anderen Sammlungen unverändert. Da dieses Log append-only ist
 * (nie überschrieben), bleiben bereits angelegte Einträge dauerhaft unter den alten Namen -
 * [toReadingOrNull] liest deshalb beide.
 */
class SensorReadingRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("sensor_readings")

    /** Verlauf eines Sensors, neueste zuerst - für `SensorDetailActivity`. */
    fun observeReadings(sensorId: String): Flow<List<SensorReading>> = callbackFlow {
        val registration = collection
            .whereEqualTo(FIELD_SENSOR_ID, sensorId)
            .addSnapshotListener { snapshot, _ ->
                val readings = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toReadingOrNull() }
                    .sortedByDescending { it.measuredAt?.time ?: 0L }
                trySend(readings)
            }
        awaitClose { registration.remove() }
    }

    /** Einmalige Abfrage (kein Listener) für den Prognose-Hintergrundabgleich. */
    suspend fun loadReadings(sensorId: String): List<SensorReading> =
        collection.whereEqualTo(FIELD_SENSOR_ID, sensorId).get().await().documents.mapNotNull { it.toReadingOrNull() }

    suspend fun recordReading(uid: String, sensorId: String, temperatureC: Double, humidityPercent: Double) {
        val data = mapOf(
            FIELD_UID to uid,
            FIELD_SENSOR_ID to sensorId,
            FIELD_MEASURED_AT to FieldValue.serverTimestamp(),
            FIELD_TEMPERATURE to temperatureC,
            FIELD_HUMIDITY to humidityPercent,
        )
        collection.add(data).await()
    }

    /** Mehrere historische Messwerte (z. B. aus [SensorBleScanner.readHistory]) auf einmal
     * anlegen - anders als [recordReading] mit einem selbst gesetzten Zeitpunkt statt
     * `serverTimestamp()`, da diese Werte in der Vergangenheit liegen. In Batches zu höchstens
     * 450 Einträgen geschrieben (Firestore-Limit pro Batch: 500). */
    suspend fun recordHistoricalReadings(uid: String, sensorId: String, points: List<HistoricalPoint>) {
        if (points.isEmpty()) return
        points.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { point ->
                batch.set(
                    collection.document(),
                    mapOf(
                        FIELD_UID to uid,
                        FIELD_SENSOR_ID to sensorId,
                        FIELD_MEASURED_AT to point.measuredAt,
                        FIELD_TEMPERATURE to point.temperatureC,
                        FIELD_HUMIDITY to point.humidityPercent,
                    ),
                )
            }
            batch.commit().await()
        }
    }

    private fun DocumentSnapshot.toReadingOrNull(): SensorReading? {
        val uid = getString(FIELD_UID) ?: return null
        val sensorId = getString(FIELD_SENSOR_ID) ?: return null
        return SensorReading(
            id = id,
            uid = uid,
            sensorId = sensorId,
            measuredAt = (getTimestamp(FIELD_MEASURED_AT) ?: getTimestamp(LEGACY_MEASURED_AT))?.toDate(),
            temperatureC = getDouble(FIELD_TEMPERATURE) ?: getDouble(LEGACY_TEMPERATURE) ?: 0.0,
            humidityPercent = getDouble(FIELD_HUMIDITY) ?: getDouble(LEGACY_HUMIDITY) ?: 0.0,
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
        private const val FIELD_SENSOR_ID = "sensorId"

        // Verständlichere deutsche Feldnamen, siehe Klassen-Doc.
        private const val FIELD_MEASURED_AT = "gemessenAm"
        private const val FIELD_TEMPERATURE = "temperaturC"
        private const val FIELD_HUMIDITY = "feuchtigkeitProzent"

        // Alte Feldnamen, nur noch für den Lese-Fallback auf bereits angelegte Einträge - dieses
        // Log ist append-only und wird nie umgeschrieben, siehe Klassen-Doc.
        private const val LEGACY_MEASURED_AT = "measuredAt"
        private const val LEGACY_TEMPERATURE = "temperatureC"
        private const val LEGACY_HUMIDITY = "humidityPercent"
    }
}
