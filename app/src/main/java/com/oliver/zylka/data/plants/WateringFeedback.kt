package com.oliver.zylka.data.plants

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Optionale Rückmeldung beim Gießen, die zusätzlich zum errechneten Verbrauch in die
 * Selbstkalibrierung von [Pot.kapazitaetMm] einfließt (siehe [PlantWaterCalculator]). */
enum class WateringFeedback(
    val id: String,
    @param:StringRes val label: Int,
) {
    UEBERFAELLIG(id = "UEBERFAELLIG", label = R.string.watering_feedback_ueberfaellig),
    PASSEND(id = "PASSEND", label = R.string.watering_feedback_passend),
    NOCH_FEUCHT(id = "NOCH_FEUCHT", label = R.string.watering_feedback_noch_feucht),
    ;

    companion object {
        fun fromId(id: String?): WateringFeedback? = entries.firstOrNull { it.id == id }
    }
}
