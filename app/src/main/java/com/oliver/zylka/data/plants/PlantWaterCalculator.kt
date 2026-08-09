package com.oliver.zylka.data.plants

import kotlin.math.PI

/** Eine stündliche Wetter-Messung/-Prognose: Referenzverdunstung ET0 (mm/h) und
 * Niederschlag (mm) für diese Stunde, an einem festen Zeitpunkt. */
data class HourlySample(
    val epochMillis: Long,
    val et0MmPerHour: Double,
    val precipitationMm: Double,
)

/** Ergebnis der geometrischen Startkapazitäts-Berechnung aus dem Topfdurchmesser. */
data class CapacityEstimate(val kapazitaetMm: Double, val volumenLiter: Double)

/** Vorratskurve (Zeitpunkt -> Vorrat in mm), aktueller Vorrat "jetzt" und der berechnete
 * Fälligkeitszeitpunkt (oder null, wenn die Wetterreihe dafür nicht reicht). */
data class PotSimulation(
    val verlauf: List<Pair<Long, Double>>,
    val vorratJetztMm: Double,
    val faelligAbEpochMillis: Long?,
)

/**
 * Wasserbilanzmodell für den Gießplaner - komplett ohne Android-Abhängigkeiten, damit alle
 * Formeln an einer Stelle stehen (Vorbild: `NotenspiegelCalculator`). Kein Feuchtesensor:
 * die Prognose ergibt sich aus der Referenzverdunstung (ET0, aus Wetterdaten) und kalibriert
 * sich über [recalibrateCapacity] durch das tatsächliche Gießverhalten selbst nach.
 */
object PlantWaterCalculator {

    /** Gießschwelle: unterhalb dieses Anteils der Kapazität muss wieder gegossen werden. */
    const val GIESSSCHWELLE_ANTEIL = 0.5

    private const val UNTERE_OEFFNUNG_ANTEIL = 0.7
    private const val HOEHE_ANTEIL = 0.85
    private const val PFLANZENVERFUEGBARER_ANTEIL = 0.28
    private const val KALIBRIERUNGS_FAKTOR = 0.2
    private const val FEEDBACK_UEBERFAELLIG_FAKTOR = 0.9
    private const val FEEDBACK_NOCH_FEUCHT_FAKTOR = 1.1
    private const val KAPAZITAET_MIN_ANTEIL = 0.2
    private const val KAPAZITAET_MAX_ANTEIL = 5.0
    private const val MILLIS_PRO_STUNDE = 3_600_000.0

    /** Kc_topf = Summe über alle Pflanzen im Topf von (kcBasis × groessenfaktor). */
    fun kcTopf(plants: List<Plant>): Double = plants.sumOf { it.kcBasis * it.groessenfaktor }

    /** ET_topf(t) = ET0(t) × standortfaktor × Kc_topf */
    fun etTopf(et0: Double, standortfaktor: Double, kcTopf: Double): Double = et0 * standortfaktor * kcTopf

    /** vorrat(t) = clamp(vorrat(t-1) − ET_topf(t) + regen(t) × regenfaktor, 0, kapazitaetMm) */
    fun step(vorratVorher: Double, etTopf: Double, regenMm: Double, regenfaktor: Double, kapazitaetMm: Double): Double =
        (vorratVorher - etTopf + regenMm * regenfaktor).coerceIn(0.0, kapazitaetMm)

    /**
     * Topfvolumen als Kegelstumpf (obere Öffnung = [durchmesserCm], untere Öffnung ≈ 70 %
     * davon, Höhe ≈ 0.85 × Durchmesser). Davon 28 % pflanzenverfügbares Wasser, umgerechnet
     * auf mm über die (obere) Topf-Grundfläche - das ist der Startwert für
     * `Pot.kapazitaetMm`/`kapazitaetStartwertMm`.
     */
    fun startKapazitaet(durchmesserCm: Double): CapacityEstimate {
        if (durchmesserCm <= 0.0) return CapacityEstimate(kapazitaetMm = 0.0, volumenLiter = 0.0)
        val rObenM = durchmesserCm / 200.0
        val rUntenM = rObenM * UNTERE_OEFFNUNG_ANTEIL
        val hoeheM = (durchmesserCm / 100.0) * HOEHE_ANTEIL
        // Kegelstumpf-Volumen: V = (π × h / 3) × (R² + R×r + r²)
        val volumenM3 = (PI * hoeheM / 3.0) * (rObenM * rObenM + rObenM * rUntenM + rUntenM * rUntenM)
        val volumenLiter = volumenM3 * 1000.0
        val verfuegbarLiter = volumenLiter * PFLANZENVERFUEGBARER_ANTEIL
        val grundflaecheM2 = PI * rObenM * rObenM
        val kapazitaetMm = if (grundflaecheM2 > 0.0) (verfuegbarLiter / grundflaecheM2) else 0.0
        return CapacityEstimate(kapazitaetMm = kapazitaetMm, volumenLiter = volumenLiter)
    }

    /**
     * Läuft die Wasserbilanz stündlich durch [hourly] (chronologisch sortiert, ein Eintrag
     * pro Zeitpunkt) und liefert für jeden Zeitpunkt den resultierenden Vorrat.
     */
    fun simulate(
        vorratStart: Double,
        hourly: List<HourlySample>,
        standortfaktor: Double,
        regenfaktor: Double,
        kcTopf: Double,
        kapazitaetMm: Double,
    ): List<Pair<Long, Double>> {
        var vorrat = vorratStart.coerceIn(0.0, kapazitaetMm)
        val result = ArrayList<Pair<Long, Double>>(hourly.size)
        for (sample in hourly) {
            val et = etTopf(sample.et0MmPerHour, standortfaktor, kcTopf)
            vorrat = step(vorrat, et, sample.precipitationMm, regenfaktor, kapazitaetMm)
            result.add(sample.epochMillis to vorrat)
        }
        return result
    }

    /** Erster Zeitpunkt in [verlauf], an dem der Vorrat unter die Gießschwelle fällt, oder
     * null, wenn die Reihe dafür nicht reicht. */
    fun forecastDueAt(
        verlauf: List<Pair<Long, Double>>,
        kapazitaetMm: Double,
        schwelleAnteil: Double = GIESSSCHWELLE_ANTEIL,
    ): Long? {
        val schwelle = kapazitaetMm * schwelleAnteil
        return verlauf.firstOrNull { it.second < schwelle }?.first
    }

    /**
     * Kompletter Rechenweg für einen Topf: Vorrat seit dem letzten Gießen ([wateredAtEpochMillis],
     * null = noch nie gegossen -> Start bei voller Kapazität am Anfang der Wetterreihe)
     * stündlich fortschreiben, aktuellen Vorrat ("jetzt") und die nächste Fälligkeit
     * bestimmen. [hourly] muss chronologisch sortiert sein und sowohl Vergangenheit
     * (`past_days`) als auch Zukunft (`forecast_days`) abdecken.
     *
     * Liegt [wateredAtEpochMillis] vor dem ersten Eintrag von [hourly] (die App war länger
     * nicht offen, als die Wetter-Rückschau reicht), wird die Lücke einmalig mit der
     * mittleren ET0 der verfügbaren Woche als Pauschalabzug überbrückt, bevor die stündliche
     * Simulation weiterläuft.
     */
    fun computeForecast(
        wateredAtEpochMillis: Long?,
        nowEpochMillis: Long,
        hourly: List<HourlySample>,
        standortfaktor: Double,
        regenfaktor: Double,
        kcTopf: Double,
        kapazitaetMm: Double,
        schwelleAnteil: Double = GIESSSCHWELLE_ANTEIL,
    ): PotSimulation {
        if (hourly.isEmpty() || kapazitaetMm <= 0.0) {
            return PotSimulation(verlauf = emptyList(), vorratJetztMm = kapazitaetMm, faelligAbEpochMillis = null)
        }
        val sorted = hourly.sortedBy { it.epochMillis }
        val firstSampleTime = sorted.first().epochMillis
        val effectiveStart = maxOf(wateredAtEpochMillis ?: firstSampleTime, firstSampleTime)

        var startVorrat = kapazitaetMm
        if (wateredAtEpochMillis != null && wateredAtEpochMillis < firstSampleTime) {
            val meanEt0 = sorted.map { it.et0MmPerHour }.average()
            val missingHours = (firstSampleTime - wateredAtEpochMillis) / MILLIS_PRO_STUNDE
            val etTopfMean = etTopf(meanEt0, standortfaktor, kcTopf)
            startVorrat = (kapazitaetMm - missingHours * etTopfMean).coerceIn(0.0, kapazitaetMm)
        }

        val relevant = sorted.filter { it.epochMillis >= effectiveStart }
        val verlauf = simulate(startVorrat, relevant, standortfaktor, regenfaktor, kcTopf, kapazitaetMm)

        val vorratJetzt = verlauf.lastOrNull { it.first <= nowEpochMillis }?.second ?: startVorrat
        val future = verlauf.filter { it.first >= nowEpochMillis }
        val faelligAb = forecastDueAt(future, kapazitaetMm, schwelleAnteil)

        return PotSimulation(verlauf = verlauf, vorratJetztMm = vorratJetzt, faelligAbEpochMillis = faelligAb)
    }

    /**
     * Passt `kapazitaetMm` nach einem Gießvorgang an: `verhaeltnis` = tatsächlich
     * verbrauchte Menge bis zum Gießen / Gießschwelle (50 % der alten Kapazität). War der
     * Vorrat schon länger unter der Schwelle (verhaeltnis > 1), war die Kapazität zu niedrig
     * angesetzt und wird erhöht; wurde schon vor Erreichen der Schwelle gegossen
     * (verhaeltnis < 1), wird sie gesenkt. Das Feedback verschiebt zusätzlich ±10 %.
     * Ergebnis auf 20-500 % des geometrischen Startwerts begrenzt.
     */
    fun recalibrateCapacity(
        kapazitaetMm: Double,
        kapazitaetStartwertMm: Double,
        verbrauchtBisGiessenMm: Double,
        feedback: WateringFeedback?,
        schwelleAnteil: Double = GIESSSCHWELLE_ANTEIL,
    ): Double {
        val schwelle = kapazitaetMm * schwelleAnteil
        var neu = if (schwelle > 0.0) {
            val verhaeltnis = verbrauchtBisGiessenMm / schwelle
            kapazitaetMm * (1 + KALIBRIERUNGS_FAKTOR * (verhaeltnis - 1))
        } else {
            kapazitaetMm
        }
        neu = when (feedback) {
            WateringFeedback.UEBERFAELLIG -> neu * FEEDBACK_UEBERFAELLIG_FAKTOR
            WateringFeedback.NOCH_FEUCHT -> neu * FEEDBACK_NOCH_FEUCHT_FAKTOR
            WateringFeedback.PASSEND, null -> neu
        }
        if (kapazitaetStartwertMm <= 0.0) return neu
        val min = kapazitaetStartwertMm * KAPAZITAET_MIN_ANTEIL
        val max = kapazitaetStartwertMm * KAPAZITAET_MAX_ANTEIL
        return neu.coerceIn(min, max)
    }
}
