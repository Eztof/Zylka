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
     * aufgerufen. */
    suspend fun updateLastReading(sensorId: String, temperatureC: Double, humidityPercent: Double) {
        collection.document(sensorId).update(
            mapOf(
                "lastTemperatureC" to temperatureC,
                "lastHumidityPercent" to humidityPercent,
                "lastMeasuredAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun Sensor.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "name" to name,
        "macAddress" to macAddress,
        "lastTemperatureC" to lastTemperatureC,
        "lastHumidityPercent" to lastHumidityPercent,
    )

    private fun DocumentSnapshot.toSensorOrNull(): Sensor? {
        val uid = getString(FIELD_UID) ?: return null
        return Sensor(
            id = id,
            uid = uid,
            name = getString("name") ?: "",
            macAddress = getString("macAddress") ?: "",
            lastTemperatureC = getDouble("lastTemperatureC"),
            lastHumidityPercent = getDouble("lastHumidityPercent"),
            lastMeasuredAt = getTimestamp("lastMeasuredAt")?.toDate(),
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
