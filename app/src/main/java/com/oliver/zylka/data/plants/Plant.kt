package com.oliver.zylka.data.plants

/**
 * Eine Pflanze (oder mehrere gleiche, siehe [anzahl]) in einem [Pot]. [kcBasis] wird beim
 * Anlegen aus [kategorie] vorbelegt, ist aber frei überschreibbar; [groessenfaktor] skaliert
 * zusätzlich nach tatsächlicher Pflanzengröße (Default 1.0). [anzahl] (Default 1) steht für
 * mehrere gleiche Pflanzen im selben Topf (z. B. 10 Tomatenpflanzen als ein Eintrag statt 10
 * einzelner) und geht als Faktor in den Verdunstungsbedarf ein.
 */
data class Plant(
    val id: String = "",
    val uid: String = "",
    val potId: String = "",
    val name: String = "",
    val kategorie: PlantCategory = PlantCategory.STANDARD,
    val kcBasis: Double = PlantCategory.STANDARD.kcBasisDefault,
    val groessenfaktor: Double = 1.0,
    val anzahl: Int = 1,
)
