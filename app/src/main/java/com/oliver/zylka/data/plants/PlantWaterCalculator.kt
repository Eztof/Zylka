package com.oliver.zylka.data.plants

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Eine stündliche Wetter-Messung/-Prognose: Referenzverdunstung ET0 (mm/h) und
 * Niederschlag (mm) für diese Stunde, an einem festen Zeitpunkt. */
data class HourlySample(
    val epochMillis: Long,
    val et0MmPerHour: Double,
    val precipitationMm: Double,
)

/** Eine über Bluetooth gemessene Temperatur/Feuchte zu einem Zeitpunkt, einem Sensor
 * zugeordnet (mehrere Sensoren können an einem Topf hängen, siehe [Plant.sensorId]). */
data class SensorSample(
    val sensorId: String,
    val epochMillis: Long,
    val temperatureC: Double,
    val humidityPercent: Double,
)

/** Ergebnis der geometrischen Startkapazitäts-Berechnung aus der Topf-Grundfläche. */
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

    private const val HOEHE_ANTEIL = 0.85
    private const val PFLANZENVERFUEGBARER_ANTEIL = 0.28
    private const val KALIBRIERUNGS_FAKTOR = 0.2
    private const val FEEDBACK_UEBERFAELLIG_FAKTOR = 0.9
    private const val FEEDBACK_NOCH_FEUCHT_FAKTOR = 1.1
    private const val KAPAZITAET_MIN_ANTEIL = 0.2
    private const val KAPAZITAET_MAX_ANTEIL = 5.0
    private const val MILLIS_PRO_STUNDE = 3_600_000.0

    /** Näherungsfaktor mm ET0 pro Stunde je kPa Sättigungsdampfdruckdefizit (VPD) - grobe
     * Größenordnung typischer ET0-Werte bei moderatem Wind, siehe [et0VonSensor]. */
    private const val VPD_ET0_FAKTOR = 0.3

    /** Wie weit eine Sensor-Messung von einer Wetter-Stunde entfernt sein darf, um ihr noch
     * zugeordnet zu werden (siehe [mergeSensorEt0]). */
    private const val MAX_SENSOR_MATCH_MILLIS = 3 * MILLIS_PRO_STUNDE

    /** Kc_topf = Summe über alle Pflanzen im Topf von (kcBasis × groessenfaktor × anzahl). */
    fun kcTopf(plants: List<Plant>): Double = plants.sumOf { it.kcBasis * it.groessenfaktor * it.anzahl }

    /** ET_topf(t) = ET0(t) × standortfaktor × Kc_topf */
    fun etTopf(et0: Double, standortfaktor: Double, kcTopf: Double): Double = et0 * standortfaktor * kcTopf

    /**
     * Näherungsweise Referenzverdunstung (mm/h) aus Temperatur und relativer Luftfeuchte
     * eines TP357-Sensors, über das Sättigungsdampfdruckdefizit (VPD - "wie durstig ist die
     * Luft"), mit derselben Tetens-Formel für den Sättigungsdampfdruck wie in FAO-56. Deutlich
     * einfacher als eine echte Penman-Monteith-ET0 (kein Wind, keine Strahlung), aber die
     * Selbstkalibrierung ([recalibrateCapacity]/`standortfaktor`) gleicht systematische
     * Abweichungen ohnehin aus - die Formel muss vor allem die Richtung stimmen: wärmer/
     * trockener → mehr Verdunstung.
     */
    fun et0VonSensor(temperatureC: Double, humidityPercent: Double): Double {
        val saettigungsdampfdruckKPa = 0.6108 * exp(17.27 * temperatureC / (temperatureC + 237.3))
        val vpdKPa = saettigungsdampfdruckKPa * (1.0 - (humidityPercent / 100.0).coerceIn(0.0, 1.0))
        return (VPD_ET0_FAKTOR * vpdKPa).coerceAtLeast(0.0)
    }

    /**
     * Ersetzt in [hourly] für Zeitpunkte bis [nowEpochMillis] (Vergangenheit) die API-ET0
     * durch eine aus [sensorSamples] abgeleitete ET0 ([et0VonSensor]), sofern für diese
     * Stunde eine Messung höchstens [MAX_SENSOR_MATCH_MILLIS] entfernt liegt - sonst bleibt
     * der API-Wert stehen. Für Zeitpunkte nach "jetzt" (Prognose) wird immer die API-ET0
     * verwendet, kein Sensor kann die Zukunft messen. Hängen mehrere Sensoren am Topf (mehrere
     * Pflanzen mit unterschiedlichem Sensor), wird pro Stunde über deren jeweils nächstgelegene
     * Messung gemittelt.
     */
    fun mergeSensorEt0(
        hourly: List<HourlySample>,
        sensorSamples: List<SensorSample>,
        nowEpochMillis: Long,
    ): List<HourlySample> {
        if (sensorSamples.isEmpty()) return hourly
        val bySensor = sensorSamples.groupBy { it.sensorId }
        return hourly.map { sample ->
            if (sample.epochMillis > nowEpochMillis) return@map sample
            val naechsteJeSensor = bySensor.values.mapNotNull { readings ->
                readings.minByOrNull { abs(it.epochMillis - sample.epochMillis) }
                    ?.takeIf { abs(it.epochMillis - sample.epochMillis) <= MAX_SENSOR_MATCH_MILLIS }
            }
            if (naechsteJeSensor.isEmpty()) return@map sample
            val temperaturMittel = naechsteJeSensor.map { it.temperatureC }.average()
            val feuchteMittel = naechsteJeSensor.map { it.humidityPercent }.average()
            sample.copy(et0MmPerHour = et0VonSensor(temperaturMittel, feuchteMittel))
        }
    }

    /** vorrat(t) = clamp(vorrat(t-1) − ET_topf(t) + regen(t) × regenfaktor, 0, kapazitaetMm) */
    fun step(vorratVorher: Double, etTopf: Double, regenMm: Double, regenfaktor: Double, kapazitaetMm: Double): Double =
        (vorratVorher - etTopf + regenMm * regenfaktor).coerceIn(0.0, kapazitaetMm)

    /**
     * Schätzt Kapazität und Volumen aus der Topf-Grundfläche ([grundflaecheCm2]) - bewusst
     * flächenbasiert statt durchmesserbasiert, damit sowohl runde (π × r²) als auch eckige
     * Töpfe (Länge × Breite) erfasst werden können. Die Tiefe lässt sich aus einer reinen
     * Flächenangabe nicht ableiten, daher wird sie über eine charakteristische Kantenlänge
     * (√Grundfläche) geschätzt: Höhe ≈ 0.85 × √Grundfläche, Topf als einfache Säule (kein
     * Verjüngen mehr, das bei eckigen Töpfen ohnehin nicht allgemeingültig wäre). Davon 28 %
     * pflanzenverfügbares Wasser, als Wasserhöhe (mm) ausgedrückt - das ist der Startwert für
     * `Pot.kapazitaetMm`/`kapazitaetStartwertMm`. Bei einer Säule kürzt sich die Grundfläche
     * dabei heraus: die Kapazität in mm hängt nur von der geschätzten Tiefe ab, nicht von der
     * Fläche - genau wie ET0 selbst ist sie eine reine Tiefenangabe pro Fläche.
     */
    fun startKapazitaet(grundflaecheCm2: Double): CapacityEstimate {
        if (grundflaecheCm2 <= 0.0) return CapacityEstimate(kapazitaetMm = 0.0, volumenLiter = 0.0)
        val kantenlaengeCm = sqrt(grundflaecheCm2)
        val hoeheCm = kantenlaengeCm * HOEHE_ANTEIL
        val volumenLiter = grundflaecheCm2 * hoeheCm / 1000.0
        val kapazitaetMm = hoeheCm * 10.0 * PFLANZENVERFUEGBARER_ANTEIL
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
