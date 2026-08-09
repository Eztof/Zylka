package com.oliver.zylka.data.plants

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Pflanzen (`plants/{plantId}`) - geteilt zwischen allen eingeloggten Nutzern
 * (gemeinsamer Garten). Wie bei [com.oliver.zylka.data.kennzeichen.DiscoveryRepository] wird
 * ein Listener über alle Pflanzen genutzt und clientseitig nach `potId` gruppiert, statt pro
 * Topf einen eigenen Listener zu eröffnen.
 */
class PlantRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("plants")

    fun observePlants(): Flow<List<Plant>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toPlantOrNull() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun loadPlants(): List<Plant> =
        collection.get().await().documents.mapNotNull { it.toPlantOrNull() }

    /** Einmaliges Laden einer einzelnen Pflanze, z. B. zum Öffnen des Bearbeiten-Formulars. */
    suspend fun getPlant(plantId: String): Plant? = collection.document(plantId).get().await().toPlantOrNull()

    /** Alle Pflanzen eines Topfs - für die Mini-Liste in `PotEditActivity`. */
    suspend fun loadPlantsForPot(potId: String): List<Plant> =
        collection.whereEqualTo("potId", potId).get().await().documents.mapNotNull { it.toPlantOrNull() }

    suspend fun save(plant: Plant): String {
        val data = plant.toMap()
        return if (plant.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(plant.id).set(data).await()
            plant.id
        }
    }

    suspend fun delete(plantId: String) {
        collection.document(plantId).delete().await()
    }

    private fun Plant.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "potId" to potId,
        "name" to name,
        "kategorie" to kategorie.id,
        "kcBasis" to kcBasis,
        "groessenfaktor" to groessenfaktor,
        "anzahl" to anzahl,
    )

    private fun DocumentSnapshot.toPlantOrNull(): Plant? {
        val uid = getString(FIELD_UID) ?: return null
        val potId = getString("potId") ?: return null
        val kategorie = PlantCategory.fromId(getString("kategorie"))
        return Plant(
            id = id,
            uid = uid,
            potId = potId,
            name = getString("name") ?: "",
            kategorie = kategorie,
            kcBasis = getDouble("kcBasis") ?: kategorie.kcBasisDefault,
            groessenfaktor = getDouble("groessenfaktor") ?: 1.0,
            anzahl = getLong("anzahl")?.toInt()?.coerceAtLeast(1) ?: 1,
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
