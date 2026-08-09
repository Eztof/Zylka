package com.oliver.zylka.data.plants

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Stündliche Wetterreihe für einen Topf, plus Herkunft: [isStale] = aus dem Cache, weil der
 * letzte Netzabruf fehlgeschlagen ist (die UI weist dann darauf hin). */
data class WeatherFetchResult(
    val hourly: List<HourlySample>,
    val fetchedAtEpochMillis: Long,
    val isStale: Boolean,
)

/**
 * Liefert die stündliche Referenzverdunstung (ET0) und Niederschlag für einen Standort, von
 * [Open-Meteo](https://open-meteo.com/en/docs) (kostenlos, kein API-Key). Nutzt dieselbe
 * einfache HTTP-Mechanik wie [com.oliver.zylka.update.UpdateManager]
 * (`HttpURLConnection` + `org.json`, keine neue Gradle-Abhängigkeit).
 *
 * Antworten werden in Firestore (`weather_cache/{uid}`) zwischengespeichert, ein Eintrag pro
 * Standort (gerundet auf 2 Nachkommastellen, ~1 km) - mehrere Töpfe am selben Standort teilen
 * sich einen Cache-Eintrag. Höchstens ein Netzabruf alle 3 Stunden pro Standort; schlägt der
 * Abruf fehl (kein Netz), wird mit dem letzten Cache-Stand weitergerechnet und das über
 * [WeatherFetchResult.isStale] kenntlich gemacht.
 *
 * Bewusst als eigene, austauschbare Quelle gehalten: eine spätere zweite Quelle (z. B. ein
 * lokales TP357-Bluetooth-Thermo-Hygrometer für eine Mikroklima-Korrektur) könnte hier
 * danebengestellt werden, ohne die Aufrufer ([PlantForecastRepository]) anzufassen - das ist
 * in diesem Schritt aber noch nicht umgesetzt.
 */
class WeatherRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    suspend fun hourlySeries(uid: String, latitude: Double, longitude: Double): WeatherFetchResult =
        withContext(Dispatchers.IO) {
            val bucket = locationBucket(latitude, longitude)
            val docRef = firestore.collection(COLLECTION).document(uid)
            val cached = runCatching { loadCachedBucket(docRef, bucket) }.getOrNull()

            if (cached != null && System.currentTimeMillis() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS) {
                return@withContext cached.copy(isStale = false)
            }

            try {
                val hourly = fetchFromNetwork(latitude, longitude)
                val fetchedAt = System.currentTimeMillis()
                runCatching { saveCachedBucket(docRef, bucket, latitude, longitude, hourly) }
                WeatherFetchResult(hourly, fetchedAt, isStale = false)
            } catch (e: Exception) {
                cached?.copy(isStale = true) ?: throw e
            }
        }

    private fun locationBucket(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)

    private suspend fun loadCachedBucket(docRef: DocumentReference, bucket: String): WeatherFetchResult? {
        val snapshot = docRef.get().await()
        val locations = snapshot.get("locations") as? Map<*, *> ?: return null
        val entry = locations[bucket] as? Map<*, *> ?: return null
        val fetchedAt = (entry["fetchedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: return null
        val hourlyMap = entry["hourly"] as? Map<*, *> ?: return null
        val times = (hourlyMap["time"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: return null
        val et0 = (hourlyMap["et0"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: return null
        val precipitation = (hourlyMap["precipitation"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: return null
        if (times.size != et0.size || times.size != precipitation.size) return null
        val hourly = times.indices.map { i -> HourlySample(times[i], et0[i], precipitation[i]) }
        return WeatherFetchResult(hourly, fetchedAt, isStale = false)
    }

    private suspend fun saveCachedBucket(
        docRef: DocumentReference,
        bucket: String,
        latitude: Double,
        longitude: Double,
        hourly: List<HourlySample>,
    ) {
        val entry = mapOf(
            "fetchedAt" to FieldValue.serverTimestamp(),
            "latitude" to latitude,
            "longitude" to longitude,
            "hourly" to mapOf(
                "time" to hourly.map { it.epochMillis },
                "et0" to hourly.map { it.et0MmPerHour },
                "precipitation" to hourly.map { it.precipitationMm },
            ),
        )
        try {
            // Nur den einen Standort-Eintrag mergen (Punktnotation = Feldpfad), damit die
            // Cache-Einträge anderer Töpfe/Standorte unangetastet bleiben.
            docRef.update(mapOf("locations.$bucket" to entry)).await()
        } catch (e: Exception) {
            // Dokument existiert noch nicht.
            docRef.set(mapOf("locations" to mapOf(bucket to entry))).await()
        }
    }

    private fun fetchFromNetwork(latitude: Double, longitude: Double): List<HourlySample> {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&hourly=et0_fao_evapotranspiration,precipitation,temperature_2m,relative_humidity_2m" +
                "&past_days=7&forecast_days=7&timezone=Europe%2FBerlin"
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Wetter-Abruf fehlgeschlagen: HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseHourly(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Nur ET0 und Niederschlag fließen in [PlantWaterCalculator] ein - Temperatur und
     * Luftfeuchte werden bewusst mit abgefragt (Vorgabe), aber hier nicht weiterverarbeitet. */
    private fun parseHourly(json: String): List<HourlySample> {
        val hourly = JSONObject(json).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val et0 = hourly.getJSONArray("et0_fao_evapotranspiration")
        val precipitation = hourly.getJSONArray("precipitation")
        val zone = ZoneId.of("Europe/Berlin")
        return (0 until times.length()).map { i ->
            val epochMillis = LocalDateTime.parse(times.getString(i)).atZone(zone).toInstant().toEpochMilli()
            HourlySample(
                epochMillis = epochMillis,
                et0MmPerHour = if (et0.isNull(i)) 0.0 else et0.getDouble(i),
                precipitationMm = if (precipitation.isNull(i)) 0.0 else precipitation.getDouble(i),
            )
        }
    }

    companion object {
        private const val COLLECTION = "weather_cache"
        private const val CACHE_TTL_MILLIS = 3 * 60 * 60 * 1000L
    }
}
