package com.oliver.zylka.data.plants

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Töpfe (`pots/{potId}`) - geteilt zwischen allen eingeloggten Nutzern
 * (gemeinsamer Garten), kein lokaler/Offline-Zustand. Alles läuft über Snapshot-Listener bzw.
 * (für den Prognose-Hintergrundabgleich in [PlantForecastRepository]/`PlantAlarmScheduler`, wo
 * kein Listener sauber wieder abgemeldet werden könnte) einmalige Abfragen.
 */
class PotRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("pots")

    fun observePots(): Flow<List<Pot>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toPotOrNull() })
        }
        awaitClose { registration.remove() }
    }

    fun observePot(potId: String): Flow<Pot?> = callbackFlow {
        val registration = collection.document(potId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toPotOrNull())
        }
        awaitClose { registration.remove() }
    }

    suspend fun loadPots(): List<Pot> =
        collection.get().await().documents.mapNotNull { it.toPotOrNull() }

    /** Einmaliges Laden eines einzelnen Topfs, z. B. zum Öffnen des Bearbeiten-Formulars. */
    suspend fun getPot(potId: String): Pot? = collection.document(potId).get().await().toPotOrNull()

    /** Legt einen neuen Topf an ([Pot.id] leer) oder speichert einen bestehenden. */
    suspend fun save(pot: Pot): String {
        val data = pot.toMap()
        return if (pot.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(pot.id).set(data).await()
            pot.id
        }
    }

    suspend fun delete(potId: String) {
        collection.document(potId).delete().await()
    }

    private fun Pot.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "name" to name,
        "durchmesserCm" to durchmesserCm,
        "volumenLiter" to volumenLiter,
        "standort" to standort.id,
        "kapazitaetMm" to kapazitaetMm,
        "kapazitaetStartwertMm" to kapazitaetStartwertMm,
        "standortfaktor" to standortfaktor,
        "latitude" to latitude,
        "longitude" to longitude,
    )

    private fun DocumentSnapshot.toPotOrNull(): Pot? {
        val uid = getString(FIELD_UID) ?: return null
        val standort = Standort.fromId(getString("standort"))
        return Pot(
            id = id,
            uid = uid,
            name = getString("name") ?: "",
            durchmesserCm = getDouble("durchmesserCm") ?: 0.0,
            volumenLiter = getDouble("volumenLiter") ?: 0.0,
            standort = standort,
            kapazitaetMm = getDouble("kapazitaetMm") ?: 0.0,
            kapazitaetStartwertMm = getDouble("kapazitaetStartwertMm") ?: 0.0,
            standortfaktor = getDouble("standortfaktor") ?: standort.standortfaktorDefault,
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
