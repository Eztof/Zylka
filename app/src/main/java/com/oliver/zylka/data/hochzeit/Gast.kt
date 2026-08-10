package com.oliver.zylka.data.hochzeit

/** Ein potenzieller Hochzeitsgast - [prioritaet] drückt aus, wie sicher eine Einladung/das
 * Kommen ist (Planungs-Einschätzung), nicht einen förmlichen RSVP-Status. */
data class Gast(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val kategorie: GastKategorie = GastKategorie.FREUNDE,
    val prioritaet: GastPrioritaet = GastPrioritaet.MITTEL,
    val notiz: String = "",
)
