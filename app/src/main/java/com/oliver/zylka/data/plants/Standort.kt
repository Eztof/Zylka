package com.oliver.zylka.data.plants

import androidx.annotation.StringRes
import com.oliver.zylka.R

/**
 * Wo ein Topf steht - bestimmt die Startwerte für [Pot.standortfaktor] (wie stark die
 * Referenzverdunstung ET0 tatsächlich am Topf ankommt) und [Pot] regenfaktor (wie viel
 * Regen den Topf erreicht). Beides sind reine Startwerte: der Standortfaktor lässt sich in
 * [com.oliver.zylka.plants.PotEditActivity] von Hand nachjustieren, kalibriert sich aber -
 * anders als [Pot.kapazitaetMm] - nicht automatisch mit.
 */
enum class Standort(
    val id: String,
    @param:StringRes val label: Int,
    val standortfaktorDefault: Double,
    val regenfaktor: Double,
) {
    FREI(id = "frei", label = R.string.standort_frei, standortfaktorDefault = 1.0, regenfaktor = 1.0),
    UNTER_DACH(id = "unterDach", label = R.string.standort_unter_dach, standortfaktorDefault = 0.5, regenfaktor = 0.0),
    INNEN(id = "innen", label = R.string.standort_innen, standortfaktorDefault = 0.25, regenfaktor = 0.0),
    ;

    companion object {
        fun fromId(id: String?): Standort = entries.firstOrNull { it.id == id } ?: FREI
    }
}
