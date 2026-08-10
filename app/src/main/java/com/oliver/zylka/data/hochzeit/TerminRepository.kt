package com.oliver.zylka.data.hochzeit

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore-backed Termin-Kandidaten (`hochzeit_termine/{autoId}`) - geteilt zwischen allen
 * eingeloggten Nutzern, wie [com.oliver.zylka.data.plants.PotRepository]. [TerminOption.datum]
 * wird als ISO-Datumsstring gespeichert ([LocalDate.toString]/[LocalDate.parse] nutzen genau
 * dieses Format), wie bei [com.oliver.zylka.data.regel.RegelRepository].
 */
class TerminRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("hochzeit_termine")

    fun observeTermine(): Flow<List<TerminOption>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toTerminOrNull() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun loadTermine(): List<TerminOption> =
        collection.get().await().documents.mapNotNull { it.toTerminOrNull() }

    suspend fun save(termin: TerminOption): String {
        val data = termin.toMap()
        return if (termin.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(termin.id).set(data).await()
            termin.id
        }
    }

    suspend fun delete(terminId: String) {
        collection.document(terminId).delete().await()
    }

    private fun TerminOption.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "terminTyp" to terminTyp.id,
        "datum" to datum.toString(),
        "status" to status.id,
        "notiz" to notiz,
        "kostenEuro" to kostenEuro,
        "geaendertAm" to FieldValue.serverTimestamp(),
    )

    private fun DocumentSnapshot.toTerminOrNull(): TerminOption? {
        val uid = getString(FIELD_UID) ?: return null
        val datum = runCatching { LocalDate.parse(getString("datum")) }.getOrNull() ?: return null
        return TerminOption(
            id = id,
            uid = uid,
            terminTyp = TerminTyp.fromId(getString("terminTyp")),
            datum = datum,
            status = TerminStatus.fromId(getString("status")),
            notiz = getString("notiz") ?: "",
            kostenEuro = getDouble("kostenEuro"),
            geaendertAm = getTimestamp("geaendertAm")?.toDate(),
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
