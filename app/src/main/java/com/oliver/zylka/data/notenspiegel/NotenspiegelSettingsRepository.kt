package com.oliver.zylka.data.notenspiegel

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Speichert die persönlichen Schwellen-Einstellungen des Notenspiegelrechners online in
 * Firestore (`notenspiegel_settings/{uid}`), damit sie auf jedem Gerät desselben Kontos
 * verfügbar sind - kein lokaler/Offline-Zustand.
 */
class NotenspiegelSettingsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun load(uid: String): NotenspiegelSettings {
        val snapshot = firestore.collection(COLLECTION).document(uid).get().await()
        if (!snapshot.exists()) return NotenspiegelSettings()

        val sechs = snapshot.doubleList(FIELD_SECHS_STUFEN)
        val funfzehn = snapshot.doubleList(FIELD_FUNFZEHN_PUNKTE)
        return NotenspiegelSettings(
            sechsStufenThresholds = sechs?.takeIf { it.size == GradingPresets.SECHS_STUFEN_DEFAULT.size }
                ?: GradingPresets.SECHS_STUFEN_DEFAULT,
            funfzehnPunkteThresholds = funfzehn?.takeIf { it.size == GradingPresets.FUNFZEHN_PUNKTE_DEFAULT.size }
                ?: GradingPresets.FUNFZEHN_PUNKTE_DEFAULT,
        )
    }

    suspend fun save(uid: String, settings: NotenspiegelSettings) {
        val data = mapOf(
            FIELD_SECHS_STUFEN to settings.sechsStufenThresholds,
            FIELD_FUNFZEHN_PUNKTE to settings.funfzehnPunkteThresholds,
        )
        firestore.collection(COLLECTION).document(uid).set(data).await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.doubleList(field: String): List<Double>? =
        (get(field) as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }

    companion object {
        private const val COLLECTION = "notenspiegel_settings"
        private const val FIELD_SECHS_STUFEN = "sechsStufenThresholds"
        private const val FIELD_FUNFZEHN_PUNKTE = "funfzehnPunkteThresholds"
    }
}
