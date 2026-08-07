package com.oliver.zylka.data

/**
 * Beschreibt die aktuell veröffentlichte App-Version, wie sie im
 * Firestore-Dokument "app_config/version" hinterlegt ist.
 *
 * Felder im Dokument:
 * - versionCode (number): fortlaufende Nummer, muss zu jeder Veröffentlichung
 *   erhöht werden (siehe app/build.gradle.kts -> defaultConfig.versionCode)
 * - versionName (string): Anzeigename, z. B. "1.1.0"
 * - apkUrl (string): direkter Download-Link zur APK in Firebase Storage
 * - notes (string, optional): kurze Beschreibung, was sich geändert hat
 */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String? = null
)
