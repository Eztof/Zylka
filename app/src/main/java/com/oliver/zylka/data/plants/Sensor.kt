package com.oliver.zylka.data.plants

import java.util.Date

/**
 * Ein Bluetooth-Thermo-Hygrometer (ThermoPro TP357), das die Verdunstungsberechnung
 * mehrerer Pflanzen mit gemessener Temperatur/Feuchte statt der API-Prognose füttern kann
 * (siehe [Plant.sensorId], [PlantWaterCalculator.mergeSensorEt0]). [lastTemperatureC]/
 * [lastHumidityPercent]/[lastMeasuredAt] sind denormalisiert aus dem letzten
 * [SensorReading] für die Startseiten-Kachel - die eigentliche Historie steht in
 * `sensor_readings`.
 */
data class Sensor(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val macAddress: String = "",
    val lastTemperatureC: Double? = null,
    val lastHumidityPercent: Double? = null,
    val lastMeasuredAt: Date? = null,
)
