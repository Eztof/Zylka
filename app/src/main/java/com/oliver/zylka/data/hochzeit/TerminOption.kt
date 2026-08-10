package com.oliver.zylka.data.hochzeit

import java.time.LocalDate
import java.util.Date

/** Ein möglicher Termin für einen [TerminTyp] (Standesamt/Kirche/Location) - mehrere
 * Kandidaten pro Typ sind normal, bis einer [TerminStatus.BESTAETIGT] ist. */
data class TerminOption(
    val id: String = "",
    val uid: String = "",
    val terminTyp: TerminTyp = TerminTyp.STANDESAMT,
    val datum: LocalDate = LocalDate.now(),
    val status: TerminStatus = TerminStatus.VORGESCHLAGEN,
    val notiz: String = "",
    val kostenEuro: Double? = null,
    val geaendertAm: Date? = null,
)
