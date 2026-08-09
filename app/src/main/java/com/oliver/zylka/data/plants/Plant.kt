package com.oliver.zylka.data.plants

/**
 * Eine Pflanze in einem [Pot]. [species] wird in diesem Schritt nur gespeichert, nicht
 * ausgewertet (keine Artendatenbank-Anbindung). [kcBasis] wird beim Anlegen aus
 * [kategorie] vorbelegt, ist aber frei überschreibbar; [groessenfaktor] skaliert
 * zusätzlich nach tatsächlicher Pflanzengröße (Default 1.0).
 */
data class Plant(
    val id: String = "",
    val uid: String = "",
    val potId: String = "",
    val name: String = "",
    val species: String? = null,
    val kategorie: PlantCategory = PlantCategory.STANDARD,
    val kcBasis: Double = PlantCategory.STANDARD.kcBasisDefault,
    val groessenfaktor: Double = 1.0,
)
