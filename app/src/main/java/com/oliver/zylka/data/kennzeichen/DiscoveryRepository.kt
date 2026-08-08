package com.oliver.zylka.data.kennzeichen

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Log aller Funde im Kennzeichen-Sammelspiel.
 *
 * Ein Dokument pro Fund in der Collection `discoveries` (nicht ein
 * aggregiertes Array wie zuvor) - so lässt sich später jederzeit
 * nachvollziehen, wer wann welches Kennzeichen wo entdeckt hat.
 * Persönliche und globale Fortschritte werden aus demselben Log abgeleitet
 * (ein Live-Listener je Land reicht für beides plus die Chronik-Ansicht).
 *
 * Firestore-Layout: `discoveries/{autoId}`
 * { country, code, regionName, uid, userLabel, discoveredAt, latitude, longitude }
 */
class DiscoveryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("discoveries")

    /** Alle Funde für ein Land, neueste zuerst - Quelle für Fortschritt, Karte & Verlauf. */
    fun observeDiscoveries(country: Country): Flow<List<Discovery>> = callbackFlow {
        val registration = collection
            .whereEqualTo("country", country.id)
            .addSnapshotListener { snapshot, _ ->
                val discoveries = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toDiscoveryOrNull() }
                    .sortedByDescending { it.discoveredAt?.time ?: 0L }
                trySend(discoveries)
            }
        awaitClose { registration.remove() }
    }

    suspend fun recordDiscovery(
        uid: String,
        userLabel: String,
        region: PlateRegion,
        latitude: Double?,
        longitude: Double?,
    ) {
        val data = hashMapOf(
            "country" to region.country.id,
            "code" to region.code,
            "regionName" to region.name,
            "uid" to uid,
            "userLabel" to userLabel,
            "discoveredAt" to FieldValue.serverTimestamp(),
            "latitude" to latitude,
            "longitude" to longitude,
        )
        collection.add(data).await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toDiscoveryOrNull(): Discovery? {
        val country = getString("country") ?: return null
        val code = getString("code") ?: return null
        val regionName = getString("regionName") ?: return null
        val uid = getString("uid") ?: return null
        return Discovery(
            id = id,
            country = country,
            code = code,
            regionName = regionName,
            uid = uid,
            userLabel = getString("userLabel") ?: uid,
            discoveredAt = getTimestamp("discoveredAt")?.toDate(),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
        )
    }
}
