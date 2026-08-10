package com.oliver.zylka.data.hochzeit

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Dienstleister-/Kostenposten-Liste (`hochzeit_dienstleister/{autoId}`) -
 * geteilt zwischen allen eingeloggten Nutzern, wie [com.oliver.zylka.data.plants.PotRepository].
 */
class DienstleisterRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("hochzeit_dienstleister")

    fun observeDienstleister(): Flow<List<Dienstleister>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toDienstleisterOrNull() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun getDienstleister(id: String): Dienstleister? =
        collection.document(id).get().await().toDienstleisterOrNull()

    suspend fun save(dienstleister: Dienstleister): String {
        val data = dienstleister.toMap()
        return if (dienstleister.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(dienstleister.id).set(data).await()
            dienstleister.id
        }
    }

    suspend fun delete(id: String) {
        collection.document(id).delete().await()
    }

    private fun Dienstleister.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "name" to name,
        "kategorie" to kategorie.id,
        "status" to status.id,
        "kostenEuro" to kostenEuro,
        "kontakt" to kontakt,
        "notiz" to notiz,
    )

    private fun DocumentSnapshot.toDienstleisterOrNull(): Dienstleister? {
        val uid = getString(FIELD_UID) ?: return null
        return Dienstleister(
            id = id,
            uid = uid,
            name = getString("name") ?: "",
            kategorie = DienstleisterKategorie.fromId(getString("kategorie")),
            status = DienstleisterStatus.fromId(getString("status")),
            kostenEuro = getDouble("kostenEuro"),
            kontakt = getString("kontakt") ?: "",
            notiz = getString("notiz") ?: "",
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
