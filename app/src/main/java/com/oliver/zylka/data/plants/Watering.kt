package com.oliver.zylka.data.plants

import java.util.Date

/** Ein Gieß-Vorgang - append-only Log-Eintrag (Vorbild: `Discovery`/`discoveries`), aus dem
 * zusammen mit der Wetterreihe der aktuelle Wasservorrat neu berechnet wird. */
data class Watering(
    val id: String = "",
    val uid: String = "",
    val potId: String = "",
    val wateredAt: Date? = null,
    val feedback: WateringFeedback? = null,
)
