package com.oliver.zylka.data.regel

import java.time.LocalDate
import java.util.Date

/**
 * Ein Tag mit eingetragener Intensität (1-10) im Regelkalender - Tage ohne Eintrag gelten
 * implizit als Stufe 0 (siehe [RegelRepository]). Global/geteilt zwischen allen eingeloggten
 * Nutzern, wie [com.oliver.zylka.data.plants.Sensor].
 */
data class RegelEintrag(
    val datum: LocalDate,
    val intensitaet: Int,
    val uid: String = "",
    val geaendertAm: Date? = null,
)
