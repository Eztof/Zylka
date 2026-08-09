package com.oliver.zylka.data.plants

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Append-only Gieß-Log (`waterings/{autoId}`), Vorbild
 * [com.oliver.zylka.data.kennzeichen.DiscoveryRepository]: nie geändert oder gelöscht, nur
 * angelegt. Kein laufender Füllstand wird gespeichert - der Vorrat wird bei jeder Anzeige aus
 * dem letzten Eintrag hier plus der Wetterreihe neu berechnet ([PlantWaterCalculator]).
 */
class WateringRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("waterings")

    /** Verlauf eines Topfs, neueste zuerst - für `PotDetailActivity`. */
    fun observeWaterings(potId: String): Flow<List<Watering>> = callbackFlow {
        val registration = collection
            .whereEqualTo(FIELD_POT_ID, potId)
            .addSnapshotListener { snapshot, _ ->
                val waterings = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toWateringOrNull() }
                    .sortedByDescending { it.wateredAt?.time ?: 0L }
                trySend(waterings)
            }
        awaitClose { registration.remove() }
    }

    /** Einmalige Abfrage (kein Listener) für den Prognose-Hintergrundabgleich. */
    suspend fun loadWaterings(potId: String): List<Watering> =
        collection.whereEqualTo(FIELD_POT_ID, potId).get().await().documents.mapNotNull { it.toWateringOrNull() }

    suspend fun recordWatering(uid: String, potId: String, feedback: WateringFeedback?) {
        val data = mapOf(
            "uid" to uid,
            FIELD_POT_ID to potId,
            "wateredAt" to FieldValue.serverTimestamp(),
            "feedback" to feedback?.id,
        )
        collection.add(data).await()
    }

    private fun DocumentSnapshot.toWateringOrNull(): Watering? {
        val uid = getString("uid") ?: return null
        val potId = getString(FIELD_POT_ID) ?: return null
        return Watering(
            id = id,
            uid = uid,
            potId = potId,
            wateredAt = getTimestamp("wateredAt")?.toDate(),
            feedback = WateringFeedback.fromId(getString("feedback")),
        )
    }

    companion object {
        private const val FIELD_POT_ID = "potId"
    }
}
