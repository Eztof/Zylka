package com.oliver.zylka.data.plants

/**
 * Ein Pflanzgefäß - die Recheneinheit des Gießplaners. Der Wasservorrat gehört zum Topf,
 * nicht zur einzelnen Pflanze: mehrere Pflanzen können sich einen Kübel teilen und werden
 * in einem Vorgang gegossen (siehe [Plant.potId]).
 *
 * [kapazitaetMm] kalibriert sich bei jedem Gießvorgang selbst nach (siehe
 * [PlantWaterCalculator.recalibrateCapacity]); [kapazitaetStartwertMm] bleibt als
 * eingefrorener geometrischer Startwert stehen und begrenzt, wie weit sich die Kalibrierung
 * von der Realität entfernen darf (20-500 %). [standortfaktor] wird beim Anlegen aus
 * [Standort.standortfaktorDefault] übernommen und danach nur noch von Hand verändert.
 */
data class Pot(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val grundflaecheCm2: Double = 0.0,
    val volumenLiter: Double = 0.0,
    val standort: Standort = Standort.FREI,
    val kapazitaetMm: Double = 0.0,
    val kapazitaetStartwertMm: Double = 0.0,
    val standortfaktor: Double = Standort.FREI.standortfaktorDefault,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
