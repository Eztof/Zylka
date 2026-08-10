package com.oliver.zylka.data.hochzeit

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Wie sicher ein potenzieller Gast tatsächlich eingeladen/kommen wird (siehe [Gast]) - bewusst
 * dreistufig und locker benannt ("mäh" statt eines förmlichen Begriffs), da das hier eine grobe
 * Planungs-Einschätzung ist, keine verbindliche Zusage. */
enum class GastPrioritaet(
    val id: String,
    @param:StringRes val label: Int,
) {
    SICHER(id = "SICHER", label = R.string.hochzeit_gast_prioritaet_sicher),
    MITTEL(id = "MITTEL", label = R.string.hochzeit_gast_prioritaet_mittel),
    MAEH(id = "MAEH", label = R.string.hochzeit_gast_prioritaet_maeh),
    ;

    companion object {
        fun fromId(id: String?): GastPrioritaet = entries.firstOrNull { it.id == id } ?: MITTEL
    }
}
