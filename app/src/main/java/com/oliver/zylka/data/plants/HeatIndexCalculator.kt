package com.oliver.zylka.data.plants

/**
 * Reine Rechenlogik (kein Android-Import, wie [PlantWaterCalculator]) für die "gefühlte
 * Temperatur" aus Temperatur + relativer Luftfeuchte - wie im TP357-Sensor-UI der
 * ThermoPro-App als "Wärme-Index" gezeigt.
 */
object HeatIndexCalculator {

    /** Unterhalb dieser Temperatur weicht die gefühlte Temperatur kaum von der Ist-Temperatur
     * ab - die Rothfusz-Näherung ist dort nicht mehr aussagekräftig, siehe [heatIndexCelsius]. */
    private const val MIN_RELEVANT_CELSIUS = 26.7

    /** Unterhalb dieser Luftfeuchte ist die NOAA-Originalformel nicht mehr spezifiziert
     * (offizieller Gültigkeitsbereich: Feuchte ≥ 40 %) - deshalb wie bei [MIN_RELEVANT_CELSIUS]
     * unterhalb davon einfach die Ist-Temperatur zurückgegeben. */
    private const val MIN_RELEVANT_HUMIDITY = 40.0

    /**
     * Näherungsformel des US-Wetterdienstes (NOAA, Rothfusz-Regression) für den Hitzeindex,
     * intern in Fahrenheit gerechnet (Originalformel) und wieder nach Celsius umgerechnet. Nur
     * innerhalb ihres spezifizierten Gültigkeitsbereichs (Temperatur ≥ [MIN_RELEVANT_CELSIUS],
     * Feuchte ≥ [MIN_RELEVANT_HUMIDITY]) ausgewertet - außerhalb davon einfach die
     * Ist-Temperatur zurückgegeben (bei kühlen/trockenen Innenraum-Bedingungen ist die gefühlte
     * Temperatur ohnehin kaum unterscheidbar).
     *
     * ⚠️ Der letzte Koeffizient (`t²·r²`-Term) war ursprünglich fälschlich `-0.00199788` statt
     * `-0.00000199` (Faktor 1000 zu groß) - dadurch lieferte die Formel selbst bei normalen
     * Innenraum-Werten (z. B. 27,9 °C/37 %) mehrere Tausend Grad statt der Ist-Temperatur.
     * Gegen die bekannten NOAA-Referenzwerte geprüft (100 °F/50 % → 118,3 °F, Tabellenwert
     * 119 °F; 90 °F/70 % → 105,9 °F, Tabellenwert 106 °F).
     */
    fun heatIndexCelsius(temperatureC: Double, humidityPercent: Double): Double {
        if (temperatureC < MIN_RELEVANT_CELSIUS || humidityPercent < MIN_RELEVANT_HUMIDITY) return temperatureC
        val t = temperatureC * 9.0 / 5.0 + 32.0
        val r = humidityPercent
        val hiFahrenheit = -42.379 + 2.04901523 * t + 10.14333127 * r -
            0.22475541 * t * r - 0.00683783 * t * t - 0.05481717 * r * r +
            0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r
        return (hiFahrenheit - 32.0) * 5.0 / 9.0
    }
}
