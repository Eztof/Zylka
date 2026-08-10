package com.oliver.zylka.data.regel

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore-backed Regelkalender-Einträge (`regel_eintraege/{yyyy-MM-dd}`) - ein Dokument pro
 * Tag mit Eintrag, Dokument-ID ist das ISO-Datum ([LocalDate.toString]/[LocalDate.parse] nutzen
 * genau dieses Format, kein eigener Formatter nötig). Geteilt zwischen allen eingeloggten
 * Nutzern (wie [com.oliver.zylka.data.plants.SensorRepository]). Tage mit Stufe 0 bekommen kein
 * Dokument - siehe [setIntensitaet].
 */
class RegelRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val collection get() = db.collection("regel_eintraege")

    /** Die komplette Historie, live - Datenmenge ist klein (höchstens ein Dokument pro Tag),
     * ein einziger Listener auf die ganze Sammlung reicht (wie
     * [com.oliver.zylka.data.plants.SensorReadingRepository.observeReadings]); die Prognose
     * braucht ohnehin die volle Historie, nicht nur einen sichtbaren Monat. */
    fun observeEntries(): Flow<List<RegelEintrag>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toRegelEintragOrNull() })
        }
        awaitClose { registration.remove() }
    }

    /** Setzt die Intensität eines Tages. Bei `0` wird das Dokument gelöscht (Stufe 0 = keine
     * Regel = keine gespeicherten Daten, nicht extra als `intensitaet: 0` abgelegt). */
    suspend fun setIntensitaet(uid: String, datum: LocalDate, intensitaet: Int) {
        val document = collection.document(datum.toString())
        if (intensitaet <= 0) {
            document.delete().await()
        } else {
            document.set(
                mapOf(
                    FIELD_INTENSITAET to intensitaet,
                    FIELD_UID to uid,
                    FIELD_GEAENDERT_AM to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    private fun DocumentSnapshot.toRegelEintragOrNull(): RegelEintrag? {
        val datum = runCatching { LocalDate.parse(id) }.getOrNull() ?: return null
        val intensitaet = getLong(FIELD_INTENSITAET)?.toInt() ?: return null
        return RegelEintrag(
            datum = datum,
            intensitaet = intensitaet,
            uid = getString(FIELD_UID) ?: "",
            geaendertAm = getTimestamp(FIELD_GEAENDERT_AM)?.toDate(),
        )
    }

    companion object {
        private const val FIELD_INTENSITAET = "intensitaet"
        private const val FIELD_UID = "uid"
        private const val FIELD_GEAENDERT_AM = "geaendertAm"
    }
}
