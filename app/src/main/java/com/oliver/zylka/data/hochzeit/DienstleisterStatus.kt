package com.oliver.zylka.data.hochzeit

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Buchungs-Fortschritt eines Dienstleisters (siehe [Dienstleister]) - eine einfache Pipeline
 * von der ersten Idee bis zur festen Buchung (oder Absage). */
enum class DienstleisterStatus(
    val id: String,
    @param:StringRes val label: Int,
) {
    IDEE(id = "IDEE", label = R.string.hochzeit_dienstleister_status_idee),
    ANGEFRAGT(id = "ANGEFRAGT", label = R.string.hochzeit_dienstleister_status_angefragt),
    ANGEBOT_ERHALTEN(id = "ANGEBOT_ERHALTEN", label = R.string.hochzeit_dienstleister_status_angebot_erhalten),
    GEBUCHT(id = "GEBUCHT", label = R.string.hochzeit_dienstleister_status_gebucht),
    ABGESAGT(id = "ABGESAGT", label = R.string.hochzeit_dienstleister_status_abgesagt),
    ;

    companion object {
        fun fromId(id: String?): DienstleisterStatus = entries.firstOrNull { it.id == id } ?: IDEE
    }
}
