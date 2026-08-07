package com.oliver.zylka.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Liest die Versionsinformationen aus Firestore, die von Hand (über die
 * Firebase Console) im Dokument "app_config/version" gepflegt werden.
 */
class UpdateRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun fetchLatestVersion(): UpdateInfo? {
        val document = firestore.collection("app_config")
            .document("version")
            .get()
            .await()

        if (!document.exists()) return null

        val versionCode = document.getLong("versionCode") ?: return null
        val versionName = document.getString("versionName") ?: return null
        val apkUrl = document.getString("apkUrl") ?: return null
        val notes = document.getString("notes")

        return UpdateInfo(versionCode, versionName, apkUrl, notes)
    }
}
