package com.oliver.zylka.data.notenspiegel

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** The two grading scales used in NRW schools: the classic six-step scale (Sek I) and the
 * 15-0 point scale (gymnasiale Oberstufe/Abitur). [grades] lists every step best-to-worst;
 * [GradingPresets] holds the matching default percentage thresholds. */
enum class GradingSystem(
    val id: String,
    @param:StringRes val label: Int,
    val grades: List<String>,
) {
    SECHS_STUFEN(
        id = "sechs_stufen",
        label = R.string.notenspiegel_system_sechs_stufen,
        grades = listOf("1", "2", "3", "4", "5", "6"),
    ),
    FUNFZEHN_PUNKTE(
        id = "fuenfzehn_punkte",
        label = R.string.notenspiegel_system_fuenfzehn_punkte,
        grades = listOf("15", "14", "13", "12", "11", "10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "0"),
    ),
    ;

    companion object {
        fun fromId(id: String): GradingSystem = entries.first { it.id == id }
    }
}
