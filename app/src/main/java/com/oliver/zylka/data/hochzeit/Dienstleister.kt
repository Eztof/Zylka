package com.oliver.zylka.data.hochzeit

/** Ein Dienstleister oder Kostenposten (DJ, Catering, ...) mit Buchungs-Status und optionalen
 * Kosten - die [kostenEuro] aller [DienstleisterStatus.GEBUCHT]-Einträge ergeben die
 * Kosten-Summe auf der Hochzeit-Übersicht. */
data class Dienstleister(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val kategorie: DienstleisterKategorie = DienstleisterKategorie.SONSTIGES,
    val status: DienstleisterStatus = DienstleisterStatus.IDEE,
    val kostenEuro: Double? = null,
    val kontakt: String = "",
    val notiz: String = "",
)
