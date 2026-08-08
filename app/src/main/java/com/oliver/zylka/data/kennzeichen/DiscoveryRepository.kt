package com.oliver.zylka.data.kennzeichen

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed storage for discovered Kennzeichen codes.
 *
 * Layout:
 *  - users/{uid}/discoveries/{countryId}   { codes: [String], updatedAt }   (personal)
 *  - globalDiscoveries/{countryId}         { codes: [String], updatedAt }   (everyone, union)
 *
 * Marking a code as found writes to both documents at once (via arrayUnion, which is
 * idempotent and safe under concurrent writes from many players).
 */
class DiscoveryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun personalDoc(uid: String, country: Country): DocumentReference =
        db.collection("users").document(uid).collection("discoveries").document(country.id)

    private fun globalDoc(country: Country): DocumentReference =
        db.collection("globalDiscoveries").document(country.id)

    fun personalCodes(uid: String, country: Country): Flow<Set<String>> =
        observeCodes(personalDoc(uid, country))

    fun globalCodes(country: Country): Flow<Set<String>> =
        observeCodes(globalDoc(country))

    private fun observeCodes(ref: DocumentReference): Flow<Set<String>> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, _ ->
            @Suppress("UNCHECKED_CAST")
            val codes = (snapshot?.get("codes") as? List<String>)?.toSet() ?: emptySet()
            trySend(codes)
        }
        awaitClose { registration.remove() }
    }

    suspend fun markDiscovered(uid: String, country: Country, code: String) {
        val update = mapOf(
            "codes" to FieldValue.arrayUnion(code),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        personalDoc(uid, country).set(update, SetOptions.merge()).await()
        globalDoc(country).set(update, SetOptions.merge()).await()
    }

    suspend fun removeDiscovered(uid: String, country: Country, code: String) {
        // Only removes from the personal list - the global "someone has found this" stays true.
        personalDoc(uid, country).set(
            mapOf("codes" to FieldValue.arrayRemove(code)),
            SetOptions.merge(),
        ).await()
    }
}
