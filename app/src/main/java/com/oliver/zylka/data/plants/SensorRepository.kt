package com.oliver.zylka.data.plants

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Bluetooth-Sensoren (`sensors/{sensorId}`) - geteilt zwischen allen
 * eingeloggten Nutzern (gemeinsamer Garten), wie [PotRepository]/[PlantRepository].
 *
 * Feldnamen in Firestore sind bewusst Deutsch (`bluetoothAdresse`/`letzteTemperaturC`/
 * `letzteFeuchtigkeitProzent`/`letzteMessungAm`) statt der ursprünglichen englischen Namen
 * (`macAddress`/`lastTemperatureC`/`lastHumidityPercent`/`lastMeasuredAt`) - in der
 * Firebase-Konsole waren die englischen Namen schwer den restlichen, größtenteils deutschen
 * Sammlungen (`pots`/`plants`) zuzuordnen. Bereits vorhandene Dokumente werden beim Lesen
 * transparent unter den alten Namen mitgelesen ([toSensorOrNull]) und beim nächsten Speichern
 * automatisch auf die neuen Namen umgestellt ([toMap]/[updateLastReading]) - kein separater
 * Migrationsschritt nötig.
 */
class SensorRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("sensors")

    fun observeSensors(): Flow<List<Sensor>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toSensorOrNull() })
        }
        awaitClose { registration.remove() }
    }

    fun observeSensor(sensorId: String): Flow<Sensor?> = callbackFlow {
        val registration = collection.document(sensorId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toSensorOrNull())
        }
        awaitClose { registration.remove() }
    }

    suspend fun loadSensors(): List<Sensor> =
        collection.get().await().documents.mapNotNull { it.toSensorOrNull() }

    suspend fun getSensor(sensorId: String): Sensor? = collection.document(sensorId).get().await().toSensorOrNull()

    suspend fun save(sensor: Sensor): String {
        val data = sensor.toMap()
        return if (sensor.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(sensor.id).set(data).await()
            sensor.id
        }
    }

    suspend fun delete(sensorId: String) {
        collection.document(sensorId).delete().await()
    }

    /** Letzten Messwert denormalisiert am Sensor mitführen (für die Startseiten-Kachel, ohne
     * dafür `sensor_readings` mitzulesen). Wird direkt nach [SensorReadingRepository.recordReading]
     * aufgerufen. Räumt dabei nebenbei die alten englischen Feldnamen auf (siehe Klassen-Doc),
     * falls dieser Sensor noch nicht migriert war - `update()` ändert sonst nur die genannten
     * Felder und würde die alten sonst stehen lassen. */
    suspend fun updateLastReading(sensorId: String, temperatureC: Double, humidityPercent: Double) {
        collection.document(sensorId).update(
            mapOf(
                FIELD_LAST_TEMPERATURE to temperatureC,
                FIELD_LAST_HUMIDITY to humidityPercent,
                FIELD_LAST_MEASURED_AT to FieldValue.serverTimestamp(),
                LEGACY_MAC to FieldValue.delete(),
                LEGACY_LAST_TEMPERATURE to FieldValue.delete(),
                LEGACY_LAST_HUMIDITY to FieldValue.delete(),
                LEGACY_LAST_MEASURED_AT to FieldValue.delete(),
            ),
        ).await()
    }

    /** `set()` ersetzt das ganze Dokument (kein `merge`) - alte englische Feldnamen (siehe
     * Klassen-Doc) verschwinden dadurch von selbst, sobald ein Sensor das nächste Mal gespeichert
     * wird (z. B. beim Umbenennen), ganz ohne eigenen Migrationsschritt. [lastMeasuredAt] wird
     * hier bewusst mit ausgeschrieben (früher fehlte es in dieser Map) - sonst würde es beim
     * bloßen Umbenennen eines Sensors durch das volle `set()` verloren gehen. */
    private fun Sensor.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        FIELD_NAME to name,
        FIELD_MAC to macAddress,
        FIELD_LAST_TEMPERATURE to lastTemperatureC,
        FIELD_LAST_HUMIDITY to lastHumidityPercent,
        FIELD_LAST_MEASURED_AT to lastMeasuredAt,
    )

    /** Liest bevorzugt die neuen, verständlicheren Feldnamen - fällt für Sensoren, die seit der
     * Umbenennung noch nicht neu gespeichert wurden, auf die alten englischen Namen zurück
     * (siehe Klassen-Doc), damit bereits vorhandene Sensoren/Messwerte nicht "verschwinden". */
    private fun DocumentSnapshot.toSensorOrNull(): Sensor? {
        val uid = getString(FIELD_UID) ?: return null
        return Sensor(
            id = id,
            uid = uid,
            name = getString(FIELD_NAME) ?: "",
            macAddress = getString(FIELD_MAC) ?: getString(LEGACY_MAC) ?: "",
            lastTemperatureC = getDouble(FIELD_LAST_TEMPERATURE) ?: getDouble(LEGACY_LAST_TEMPERATURE),
            lastHumidityPercent = getDouble(FIELD_LAST_HUMIDITY) ?: getDouble(LEGACY_LAST_HUMIDITY),
            lastMeasuredAt = (getTimestamp(FIELD_LAST_MEASURED_AT) ?: getTimestamp(LEGACY_LAST_MEASURED_AT))?.toDate(),
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
        private const val FIELD_NAME = "name"

        // Verständlichere deutsche Feldnamen (Nutzerwunsch: die vorherigen englischen Namen
        // waren in der Firebase-Konsole schwer zuzuordnen). "uid" bleibt bewusst wie in
        // pots/plants/waterings; "name" ist in beiden Sprachen identisch.
        private const val FIELD_MAC = "bluetoothAdresse"
        private const val FIELD_LAST_TEMPERATURE = "letzteTemperaturC"
        private const val FIELD_LAST_HUMIDITY = "letzteFeuchtigkeitProzent"
        private const val FIELD_LAST_MEASURED_AT = "letzteMessungAm"

        // Alte Feldnamen, nur noch für den Lese-Fallback auf noch nicht migrierte Dokumente.
        private const val LEGACY_MAC = "macAddress"
        private const val LEGACY_LAST_TEMPERATURE = "lastTemperatureC"
        private const val LEGACY_LAST_HUMIDITY = "lastHumidityPercent"
        private const val LEGACY_LAST_MEASURED_AT = "lastMeasuredAt"
    }
}
