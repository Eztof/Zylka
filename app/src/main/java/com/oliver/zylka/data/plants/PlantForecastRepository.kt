package com.oliver.zylka.data.plants

import android.content.Context
import com.oliver.zylka.data.kennzeichen.LocationHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Fertige Prognose für einen Topf - Ergebnis von [PlantForecastRepository.computeForecasts].
 * [verlauf] ist die komplette simulierte Vorratskurve (Vergangenheit + Prognose, siehe
 * [PlantWaterCalculator.computeForecast]) - Basis für die 14-Tage-Kurve in `PotDetailActivity`. */
data class PotForecast(
    val pot: Pot,
    val plants: List<Plant>,
    val vorratJetztMm: Double,
    val percentFull: Int,
    val faelligAbEpochMillis: Long?,
    val isOverdue: Boolean,
    val isStale: Boolean,
    val hasLocation: Boolean,
    val verlauf: List<Pair<Long, Double>> = emptyList(),
)

/**
 * Bündelt Pots + Plants + letzte Gießvorgänge + Wetterreihe zu fertigen Prognosen. Töpfe und
 * Pflanzen sind zwischen allen eingeloggten Nutzern geteilt (gemeinsamer Garten) - geladen
 * wird hier für alle, unabhängig davon, wer sie angelegt hat. Wird sowohl von
 * `PlantsHomeActivity` (Anzeige, Sortierung nach Dringlichkeit) als auch von
 * `PlantAlarmScheduler` (nächsten Alarm bestimmen) genutzt, damit diese Kombinationslogik
 * nicht doppelt existiert - vergleichbar mit der Rolle, die `WasteCalendarRepository` für
 * sowohl `WasteCalendarActivity` als auch `WasteAlarmScheduler` spielt.
 *
 * Nutzt ausschließlich einmalige Abfragen (keine Snapshot-Listener), damit die Methode auch
 * aus einem `BroadcastReceiver` heraus (Alarm, Boot) sicher aufgerufen werden kann, ohne
 * einen Listener offen zu lassen.
 */
class PlantForecastRepository(
    context: Context,
    private val potRepository: PotRepository = PotRepository(),
    private val plantRepository: PlantRepository = PlantRepository(),
    private val wateringRepository: WateringRepository = WateringRepository(),
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val sensorReadingRepository: SensorReadingRepository = SensorReadingRepository(),
) {

    private val locationHelper = LocationHelper(context)

    /**
     * [currentUid] ist ausschließlich für den Wetter-Cache relevant (`weather_cache/{uid}`
     * gehört dem aktuell angemeldeten Nutzer - die Firestore-Regeln erlauben nur diesem
     * selbst Lese-/Schreibzugriff) - für die Auswahl der Töpfe/Pflanzen spielt er keine Rolle
     * mehr, die sind geteilt.
     */
    suspend fun computeForecasts(
        currentUid: String,
        schwelleAnteil: Double = PlantWaterCalculator.GIESSSCHWELLE_ANTEIL,
    ): List<PotForecast> = coroutineScope {
        val pots = potRepository.loadPots()
        if (pots.isEmpty()) return@coroutineScope emptyList()

        val plantsByPot = plantRepository.loadPlants().groupBy { it.potId }
        // Der Gerätestandort (bis zu 6s GPS-/Netz-Wartezeit, siehe LocationHelper) wird nur
        // abgefragt, wenn ihn mindestens ein Topf ohne eigene Koordinaten tatsächlich braucht -
        // hat jeder Topf einen eigenen Standort eingetragen, entfällt die Wartezeit komplett.
        val deviceLocation = if (pots.any { it.latitude == null || it.longitude == null }) {
            runCatching { locationHelper.currentLocationOrNull() }.getOrNull()
        } else {
            null
        }
        val now = System.currentTimeMillis()

        // Parallel statt nacheinander - jeder Topf braucht mehrere Firestore-/Wetter-Abrufe,
        // sequentiell summiert sich das spürbar auf, sobald mehr als ein Topf angelegt ist.
        val forecasts = pots.map { pot ->
            async { forecastFor(pot, plantsByPot[pot.id].orEmpty(), deviceLocation, now, schwelleAnteil, currentUid) }
        }.map { it.await() }

        forecasts.sortedWith(
            compareBy(
                { !it.hasLocation }, // Töpfe ohne bestimmbaren Standort ans Ende
                { it.faelligAbEpochMillis ?: Long.MAX_VALUE },
            ),
        )
    }

    private suspend fun forecastFor(
        pot: Pot,
        plants: List<Plant>,
        deviceLocation: Pair<Double, Double>?,
        nowEpochMillis: Long,
        schwelleAnteil: Double,
        currentUid: String,
    ): PotForecast {
        val latitude = pot.latitude ?: deviceLocation?.first
        val longitude = pot.longitude ?: deviceLocation?.second
        if (latitude == null || longitude == null) {
            return unresolvedForecast(pot, plants, isStale = false, hasLocation = false)
        }

        val weather = runCatching { weatherRepository.hourlySeries(currentUid, latitude, longitude) }.getOrNull()
        if (weather == null || weather.hourly.isEmpty()) {
            return unresolvedForecast(pot, plants, isStale = true, hasLocation = true)
        }
        val sensorSamples = loadSensorSamples(plants)
        val hourly = PlantWaterCalculator.mergeSensorEt0(weather.hourly, sensorSamples, nowEpochMillis)

        val lastWatering = wateringRepository.loadWaterings(pot.id).maxByOrNull { it.wateredAt?.time ?: 0L }
        val simulation = PlantWaterCalculator.computeForecast(
            wateredAtEpochMillis = lastWatering?.wateredAt?.time,
            nowEpochMillis = nowEpochMillis,
            hourly = hourly,
            standortfaktor = pot.standortfaktor,
            regenfaktor = pot.standort.regenfaktor,
            kcTopf = PlantWaterCalculator.kcTopf(plants),
            kapazitaetMm = pot.kapazitaetMm,
            schwelleAnteil = schwelleAnteil,
        )
        val percentFull = if (pot.kapazitaetMm > 0.0) {
            (simulation.vorratJetztMm / pot.kapazitaetMm * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        return PotForecast(
            pot = pot,
            plants = plants,
            vorratJetztMm = simulation.vorratJetztMm,
            percentFull = percentFull,
            faelligAbEpochMillis = simulation.faelligAbEpochMillis,
            isOverdue = simulation.faelligAbEpochMillis != null && simulation.faelligAbEpochMillis <= nowEpochMillis,
            isStale = weather.isStale,
            hasLocation = true,
            verlauf = simulation.verlauf,
        )
    }

    /** Lädt die Messwerte aller Sensoren, die einer der [plants] zugeordnet sind (mehrere
     * Pflanzen können denselben Sensor teilen - dann wird er nur einmal geladen). */
    private suspend fun loadSensorSamples(plants: List<Plant>): List<SensorSample> {
        val sensorIds = plants.mapNotNull { it.sensorId }.distinct()
        if (sensorIds.isEmpty()) return emptyList()
        return sensorIds.flatMap { sensorReadingRepository.loadReadings(it) }
            .mapNotNull { reading ->
                val measuredAt = reading.measuredAt?.time ?: return@mapNotNull null
                SensorSample(reading.sensorId, measuredAt, reading.temperatureC, reading.humidityPercent)
            }
    }

    private fun unresolvedForecast(pot: Pot, plants: List<Plant>, isStale: Boolean, hasLocation: Boolean) = PotForecast(
        pot = pot,
        plants = plants,
        vorratJetztMm = pot.kapazitaetMm,
        percentFull = 100,
        faelligAbEpochMillis = null,
        isOverdue = false,
        isStale = isStale,
        hasLocation = hasLocation,
    )
}
