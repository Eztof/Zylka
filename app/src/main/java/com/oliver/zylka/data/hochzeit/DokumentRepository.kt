package com.oliver.zylka.data.hochzeit

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed Dokumente/Bilder (`hochzeit_dokumente/{autoId}`) - geteilt zwischen allen
 * eingeloggten Nutzern. Die eigentlichen Datei-Inhalte liegen in Firebase Storage (siehe
 * [HochzeitStorageClient]), hier steht nur der Verweis darauf ([Dokument.storagePfad]/
 * [Dokument.downloadUrl]).
 */
class DokumentRepository(
    context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storageClient: HochzeitStorageClient = HochzeitStorageClient(context.applicationContext),
) {

    private val collection get() = db.collection("hochzeit_dokumente")

    fun observeDokumente(): Flow<List<Dokument>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toDokumentOrNull() })
        }
        awaitClose { registration.remove() }
    }

    /** Reserviert eine neue Dokument-ID, ohne schon zu schreiben - der Aufrufer baut daraus den
     * Storage-Pfad (`hochzeitsplaner/{id}.{ext}`), lädt darunter hoch und ruft danach [save]
     * mit genau dieser ID auf. So bleiben Firestore-Dokument-ID und Storage-Objektname
     * identisch, ganz ohne Dateinamens-Kollisionsprüfung. */
    fun newDocumentId(): String = collection.document().id

    suspend fun save(dokument: Dokument): String {
        val data = dokument.toMap()
        return if (dokument.id.isBlank()) {
            collection.add(data).await().id
        } else {
            collection.document(dokument.id).set(data).await()
            dokument.id
        }
    }

    /** Löscht sowohl das Firestore-Dokument als auch die Datei in Firebase Storage - der
     * Storage-Löschversuch ist best effort (siehe [HochzeitStorageClient.delete]); schlägt er
     * fehl, bleibt zwar ein verwaistes Storage-Objekt zurück, das ist aber unkritisch und soll
     * das Entfernen des Firestore-Eintrags nicht blockieren. */
    suspend fun delete(dokument: Dokument) {
        runCatching { storageClient.delete(dokument.storagePfad) }
        collection.document(dokument.id).delete().await()
    }

    private fun Dokument.toMap(): Map<String, Any?> = mapOf(
        FIELD_UID to uid,
        "name" to name,
        "mimeTyp" to mimeTyp,
        "storagePfad" to storagePfad,
        "downloadUrl" to downloadUrl,
        "verknuepfterDienstleisterId" to verknuepfterDienstleisterId,
        "hochgeladenAm" to FieldValue.serverTimestamp(),
    )

    private fun DocumentSnapshot.toDokumentOrNull(): Dokument? {
        val uid = getString(FIELD_UID) ?: return null
        return Dokument(
            id = id,
            uid = uid,
            name = getString("name") ?: "",
            mimeTyp = getString("mimeTyp") ?: "",
            storagePfad = getString("storagePfad") ?: "",
            downloadUrl = getString("downloadUrl") ?: "",
            verknuepfterDienstleisterId = getString("verknuepfterDienstleisterId")?.takeIf { it.isNotBlank() },
            hochgeladenAm = getTimestamp("hochgeladenAm")?.toDate(),
        )
    }

    companion object {
        private const val FIELD_UID = "uid"
    }
}
