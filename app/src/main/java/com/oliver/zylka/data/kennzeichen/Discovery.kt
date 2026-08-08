package com.oliver.zylka.data.kennzeichen

import java.util.Date

/**
 * One "ich habe X gesehen"-Ereignis, wie es in Firestore unter der Collection
 * `discoveries` liegt - ein Dokument pro Fund (nicht pro Nutzer/Land). Das
 * erlaubt später eine echte Chronik: wer hat wann was wo gefunden.
 */
data class Discovery(
    val id: String,
    val country: String,
    val code: String,
    val regionName: String,
    val uid: String,
    val userLabel: String,
    val discoveredAt: Date?,
    val latitude: Double?,
    val longitude: Double?,
)
