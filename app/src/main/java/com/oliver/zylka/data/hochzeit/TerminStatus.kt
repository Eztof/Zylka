package com.oliver.zylka.data.hochzeit

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Status eines vorgeschlagenen Termins (siehe [TerminOption]) - ein [TerminTyp] kann mehrere
 * Kandidat-Termine mit je eigenem Status haben, bis einer als [BESTAETIGT] feststeht. */
enum class TerminStatus(
    val id: String,
    @param:StringRes val label: Int,
) {
    VORGESCHLAGEN(id = "VORGESCHLAGEN", label = R.string.hochzeit_termin_status_vorgeschlagen),
    BESTAETIGT(id = "BESTAETIGT", label = R.string.hochzeit_termin_status_bestaetigt),
    ABGESAGT(id = "ABGESAGT", label = R.string.hochzeit_termin_status_abgesagt),
    ;

    companion object {
        fun fromId(id: String?): TerminStatus = entries.firstOrNull { it.id == id } ?: VORGESCHLAGEN
    }
}
