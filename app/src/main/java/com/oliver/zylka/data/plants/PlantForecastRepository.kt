package com.oliver.zylka.data.plants

import android.content.Context
import com.oliver.zylka.data.kennzeichen.LocationHelper

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
 * Bündelt Pots + Plants + letzte Gießvorgänge + Wetterreihe zu fertigen Prognosen. Wird
 * sowohl von `PlantsHomeActivity` (Anzeige, Sortierung nach Dringlichkeit) als auch von
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
) {

    private val locationHelper = LocationHelper(context)

    suspend fun computeForecasts(
        uid: String,
        schwelleAnteil: Double = PlantWaterCalculator.GIESSSCHWELLE_ANTEIL,
    ): List<PotForecast> {
        val pots = potRepository.loadPots(uid)
        if (pots.isEmpty()) return emptyList()

        val plantsByPot = plantRepository.loadPlants(uid).groupBy { it.potId }
        val deviceLocation = runCatching { locationHelper.currentLocationOrNull() }.getOrNull()
        val now = System.currentTimeMillis()

        val forecasts = pots.map { pot ->
            forecastFor(pot, plantsByPot[pot.id].orEmpty(), deviceLocation, now, schwelleAnteil)
        }
        return forecasts.sortedWith(
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
    ): PotForecast {
        val latitude = pot.latitude ?: deviceLocation?.first
        val longitude = pot.longitude ?: deviceLocation?.second
        if (latitude == null || longitude == null) {
            return unresolvedForecast(pot, plants, isStale = false, hasLocation = false)
        }

        val weather = runCatching { weatherRepository.hourlySeries(pot.uid, latitude, longitude) }.getOrNull()
        if (weather == null || weather.hourly.isEmpty()) {
            return unresolvedForecast(pot, plants, isStale = true, hasLocation = true)
        }

        val lastWatering = wateringRepository.loadWaterings(pot.id).maxByOrNull { it.wateredAt?.time ?: 0L }
        val simulation = PlantWaterCalculator.computeForecast(
            wateredAtEpochMillis = lastWatering?.wateredAt?.time,
            nowEpochMillis = nowEpochMillis,
            hourly = weather.hourly,
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
