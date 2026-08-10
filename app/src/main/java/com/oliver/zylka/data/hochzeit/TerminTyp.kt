package com.oliver.zylka.data.hochzeit

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Die drei Termin-Arten, die für eine Hochzeit unabhängig voneinander koordiniert werden
 * müssen - bewusst kein einzelnes "Hochzeitsdatum", da Standesamt-Termin und Kirche/Feier in
 * Deutschland oft an unterschiedlichen Tagen liegen (siehe [TerminOption]). */
enum class TerminTyp(
    val id: String,
    @param:StringRes val label: Int,
) {
    STANDESAMT(id = "STANDESAMT", label = R.string.hochzeit_termin_typ_standesamt),
    KIRCHE(id = "KIRCHE", label = R.string.hochzeit_termin_typ_kirche),
    LOCATION(id = "LOCATION", label = R.string.hochzeit_termin_typ_location),
    ;

    companion object {
        fun fromId(id: String?): TerminTyp = entries.firstOrNull { it.id == id } ?: STANDESAMT
    }
}
