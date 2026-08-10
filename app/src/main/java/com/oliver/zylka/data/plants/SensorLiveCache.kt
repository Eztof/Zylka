package com.oliver.zylka.data.plants

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prozessweiter Zwischenspeicher der zuletzt per BLE empfangenen Live-Werte (siehe
 * [SensorBleScanner.observeLiveReadings]) - rein im Arbeitsspeicher, wird **nicht** automatisch
 * in Firestore geschrieben (ein Push alle paar Sekunden würde die Datenbank zuspammen).
 *
 * Ziel: wer kurz auf „Sensoren" schaut, sieht sofort einen frischen Wert, ohne dass für die
 * Anzeige selbst erst eine neue BLE-Verbindung aufgebaut werden muss - `SensorsActivity` und
 * `SensorDetailActivity` lauschen dauerhaft (solange sichtbar) und aktualisieren diesen Cache;
 * jede Ansicht liest ihn einfach mit.
 */
object SensorLiveCache {

    private val _readings = MutableStateFlow<Map<String, BleReading>>(emptyMap())

    /** Aktueller Stand, MAC-Adressen in Großschreibung als Schlüssel - zum reaktiven Beobachten. */
    val readings: StateFlow<Map<String, BleReading>> = _readings.asStateFlow()

    fun update(macAddress: String, reading: BleReading) {
        _readings.value = _readings.value + (macAddress.uppercase() to reading)
    }

    fun get(macAddress: String): BleReading? = _readings.value[macAddress.uppercase()]
}
