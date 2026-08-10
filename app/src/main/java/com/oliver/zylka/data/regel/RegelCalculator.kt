package com.oliver.zylka.data.regel

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Ein zusammenhängender Block von Tagen mit Intensität > 0 (siehe
 * [RegelCalculator.ermittlePerioden]). [tage] sind (Tag-Offset ab 0, Intensität)-Paare. */
data class Periode(val startDatum: LocalDate, val tage: List<Pair<Int, Int>>) {
    val dauerTage: Int get() = tage.size
}

/** Eine vorhergesagte künftige Periode - [tage] sind bereits auf echte Kalendertage abgebildet. */
data class PrognosePeriode(val startDatum: LocalDate, val tage: List<Pair<LocalDate, Int>>)

/** Ergebnis von [RegelCalculator.berechnePrognose]. [unsicherheitTage] ist eine Spanne
 * (± Tage) um jedes [PrognosePeriode.startDatum], abgeleitet aus der Schwankung vergangener
 * Zyklusabstände - keine Wahrscheinlichkeitsangabe, nur eine grobe Einordnung. */
data class RegelPrognose(
    val perioden: List<PrognosePeriode>,
    val unsicherheitTage: Int,
    val anzahlPeriodenGenutzt: Int,
)

/**
 * Reine Rechenlogik (kein Android-Import, wie [com.oliver.zylka.data.plants.PlantWaterCalculator])
 * für den Regelkalender: leitet aus der eingetragenen Historie "Perioden" ab und schreibt daraus
 * künftige Zyklen fort (Zykluslänge, Dauer, Intensitätsverlauf). Eine statistische Fortschreibung
 * der eigenen Historie, kein medizinisches Modell - siehe README, Abschnitt "Grenzen des Modells".
 */
object RegelCalculator {

    /**
     * Fasst lückenlos aufeinanderfolgende Tage mit Intensität > 0 zu je einer [Periode] zusammen
     * - eine einzelne Lücke von mindestens einem Tag beendet eine Periode. [eintraege] muss nicht
     * sortiert sein.
     */
    fun ermittlePerioden(eintraege: List<RegelEintrag>): List<Periode> {
        val sortiert = eintraege.filter { it.intensitaet > 0 }.sortedBy { it.datum }
        if (sortiert.isEmpty()) return emptyList()

        val perioden = mutableListOf<Periode>()
        var periodenStart = sortiert.first().datum
        var vorherigesDatum = sortiert.first().datum
        var laufendeTage = mutableListOf(0 to sortiert.first().intensitaet)

        for (eintrag in sortiert.drop(1)) {
            val luecke = ChronoUnit.DAYS.between(vorherigesDatum, eintrag.datum)
            if (luecke <= 1) {
                val offset = ChronoUnit.DAYS.between(periodenStart, eintrag.datum).toInt()
                laufendeTage.add(offset to eintrag.intensitaet)
            } else {
                perioden.add(Periode(periodenStart, laufendeTage.toList()))
                periodenStart = eintrag.datum
                laufendeTage = mutableListOf(0 to eintrag.intensitaet)
            }
            vorherigesDatum = eintrag.datum
        }
        perioden.add(Periode(periodenStart, laufendeTage.toList()))
        return perioden
    }

    /**
     * Schreibt aus [perioden] (chronologisch sortiert, siehe [ermittlePerioden]) die nächsten
     * [anzahl] Perioden fort. `null`, wenn weniger als zwei Perioden vorliegen - dann lässt sich
     * keine Zykluslänge berechnen.
     */
    fun berechnePrognose(perioden: List<Periode>, anzahl: Int = 3): RegelPrognose? {
        if (perioden.size < 2) return null

        val zyklusabstaende = perioden.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a.startDatum, b.startDatum).toInt()
        }
        val letzteAbstaende = zyklusabstaende.takeLast(6)
        val medianAbstand = median(letzteAbstaende)
        // Ohne mehrere Abstände (nur 2 Perioden vorhanden) gibt's keine Schwankung zu messen -
        // dann bleibt es bei einer pauschalen ±1-Tag-Angabe statt 0 (0 würde Scheinsicherheit
        // vorgaukeln).
        val unsicherheitTage = maxOf(1, (letzteAbstaende.max() - letzteAbstaende.min()) / 2)

        val durchschnittsdauer = perioden.map { it.dauerTage }.average().roundToInt().coerceAtLeast(1)
        val intensitaetsverlauf = durchschnittlicherVerlauf(perioden, durchschnittsdauer)

        val prognosePerioden = mutableListOf<PrognosePeriode>()
        var start = perioden.last().startDatum.plusDays(medianAbstand.toLong())
        repeat(anzahl) {
            val tage = (0 until durchschnittsdauer).map { offset -> start.plusDays(offset.toLong()) to intensitaetsverlauf[offset] }
            prognosePerioden.add(PrognosePeriode(start, tage))
            start = start.plusDays(medianAbstand.toLong())
        }

        return RegelPrognose(prognosePerioden, unsicherheitTage, perioden.size)
    }

    /** Mittlere Intensität je Tag-Offset (0-basiert) über alle [perioden], die diesen Offset
     * erreicht haben - Länge = [maxOffset]. Erreicht keine Periode einen Offset (z. B. weil alle
     * historischen Perioden kürzer waren), wird der Gesamtdurchschnitt aller Tage als Rückfall
     * genutzt statt eine Lücke zu lassen. */
    private fun durchschnittlicherVerlauf(perioden: List<Periode>, maxOffset: Int): List<Int> {
        val gesamtdurchschnitt by lazy { perioden.flatMap { it.tage.map { tag -> tag.second } }.average().roundToInt() }
        return (0 until maxOffset).map { offset ->
            val werte = perioden.mapNotNull { periode -> periode.tage.firstOrNull { it.first == offset }?.second }
            if (werte.isEmpty()) gesamtdurchschnitt else werte.average().roundToInt()
        }
    }

    private fun median(values: List<Int>): Int {
        val sortiert = values.sorted()
        val mitte = sortiert.size / 2
        return if (sortiert.size % 2 == 0) (sortiert[mitte - 1] + sortiert[mitte]) / 2 else sortiert[mitte]
    }
}
