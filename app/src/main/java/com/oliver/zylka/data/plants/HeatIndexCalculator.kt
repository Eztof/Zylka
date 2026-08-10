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

    /**
     * Näherungsformel des US-Wetterdienstes (NOAA, Rothfusz-Regression) für den Hitzeindex,
     * intern in Fahrenheit gerechnet (Originalformel) und wieder nach Celsius umgerechnet.
     * Unterhalb von [MIN_RELEVANT_CELSIUS] (≈80 °F) liefert die Formel keine sinnvollen Werte
     * mehr - dort wird einfach die Ist-Temperatur zurückgegeben (bei kühlen/normalen
     * Innenraum-Bedingungen ist die gefühlte Temperatur ohnehin kaum unterscheidbar).
     */
    fun heatIndexCelsius(temperatureC: Double, humidityPercent: Double): Double {
        if (temperatureC < MIN_RELEVANT_CELSIUS) return temperatureC
        val t = temperatureC * 9.0 / 5.0 + 32.0
        val r = humidityPercent
        val hiFahrenheit = -42.379 + 2.04901523 * t + 10.14333127 * r -
            0.22475541 * t * r - 0.00683783 * t * t - 0.05481717 * r * r +
            0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00199788 * t * t * r * r
        return (hiFahrenheit - 32.0) * 5.0 / 9.0
    }
}
