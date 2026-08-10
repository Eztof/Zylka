package com.oliver.zylka.data.hochzeit

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Gästeliste (`hochzeit_gaeste/{autoId}`) - geteilt zwischen allen
 * eingeloggten Nutzern, wie [com.oliver.zylka.data.plants.PotRepository].
 */
class GastRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("hochzeit_gaeste")

    fun observeGaeste(): Flow<List<Gast>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toGastOrNull() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun getGast(gastId: String): Gast? = collection.document(gastId).get().await().toGastOrNull()

    suspend fun save(gast: Gast): String {
        val data = gast.toMap()
        return if (gast.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(gast.id).set(data).await()
            gast.id
        }
    }

    suspend fun delete(gastId: String) {
        collection.document(gastId).delete().await()
    }

    private fun Gast.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "name" to name,
        "kategorie" to kategorie.id,
        "prioritaet" to prioritaet.id,
        "notiz" to notiz,
    )

    private fun DocumentSnapshot.toGastOrNull(): Gast? {
        val uid = getString(FIELD_UID) ?: return null
        return Gast(
            id = id,
            uid = uid,
            name = getString("name") ?: "",
            kategorie = GastKategorie.fromId(getString("kategorie")),
            prioritaet = GastPrioritaet.fromId(getString("prioritaet")),
            notiz = getString("notiz") ?: "",
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
