package com.oliver.zylka.data.plants

import java.util.Date

/** Eine per Bluetooth ausgelesene Momentaufnahme eines [Sensor]s - append-only Log, wie
 * [Watering]. */
data class SensorReading(
    val id: String = "",
    val uid: String = "",
    val sensorId: String = "",
    val measuredAt: Date? = null,
    val temperatureC: Double = 0.0,
    val humidityPercent: Double = 0.0,
)
