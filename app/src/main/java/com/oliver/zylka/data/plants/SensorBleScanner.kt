package com.oliver.zylka.data.plants

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Ein in der Nähe gefundenes BLE-Gerät, zum Anlegen eines neuen [Sensor]s ohne die
 * MAC-Adresse von Hand abzutippen. */
data class DiscoveredDevice(val macAddress: String, val name: String?, val rssi: Int)

/** Eine über BLE ausgelesene Momentaufnahme von Temperatur/Feuchte. */
data class BleReading(val temperatureC: Double, val humidityPercent: Double)

/**
 * Liest ThermoPro TP357-Bluetooth-Thermo-Hygrometer über passives BLE-Advertisement-Scannen
 * aus - die Geräte senden Temperatur/Feuchte fortlaufend in ihren Werbepaketen, eine
 * Kopplung/Verbindung ist nicht nötig.
 *
 * **Wichtig:** Das Byte-Format der TP357-Herstellerdaten ist nicht offiziell dokumentiert.
 * [parseTp357] ist nach bestem Wissen (verbreitetes Format ähnlicher "Klon"-Sensoren)
 * implementiert, aber unverifiziert - nach dem ersten Pull unbedingt gegen die Werte in der
 * offiziellen ThermoPro-App prüfen. Unplausible Werte (außerhalb -40..60 °C bzw. 0..100 %)
 * werden bewusst verworfen (null) statt falsche Daten zu liefern.
 */
class SensorBleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** Sucht gezielt nach einem bekannten Sensor (per MAC-Adresse gefiltert) und liefert
     * dessen aktuellste Messung, oder null bei Timeout/keinem Empfang/unplausiblen Werten. */
    @SuppressLint("MissingPermission")
    suspend fun readSensor(macAddress: String, timeoutMillis: Long = 15_000): BleReading? {
        if (!hasPermissions()) return null
        val scanner = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return null
        val filter = ScanFilter.Builder().setDeviceAddress(macAddress).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val reading = parseTp357(result.scanRecord?.bytes) ?: return
                        if (cont.isActive) {
                            scanner.stopScan(this)
                            cont.resume(reading)
                        }
                    }
                }
                cont.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
                scanner.startScan(listOf(filter), settings, callback)
            }
        }
    }

    /** Ungefilterte Suche nach beliebigen BLE-Geräten in der Nähe, zum Anlegen eines neuen
     * Sensors (MAC-Adresse auswählen statt abtippen). */
    @SuppressLint("MissingPermission")
    suspend fun discoverNearbyDevices(durationMillis: Long = 8_000): List<DiscoveredDevice> {
        if (!hasPermissions()) return emptyList()
        val scanner = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return emptyList()
        val found = LinkedHashMap<String, DiscoveredDevice>()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                found[result.device.address] = DiscoveredDevice(
                    macAddress = result.device.address,
                    name = result.scanRecord?.deviceName ?: result.device.name,
                    rssi = result.rssi,
                )
            }
        }
        scanner.startScan(emptyList(), settings, callback)
        delay(durationMillis)
        runCatching { scanner.stopScan(callback) }
        return found.values.sortedByDescending { it.rssi }
    }

    /** Läuft die AD-Struktur eines BLE-Werbepakets durch ([Länge][Typ][Daten…]) und versucht,
     * TP357-Herstellerdaten (Typ 0xFF) zu erkennen und zu parsen. */
    private fun parseTp357(advertisementBytes: ByteArray?): BleReading? {
        if (advertisementBytes == null) return null
        var index = 0
        while (index < advertisementBytes.size) {
            val length = advertisementBytes[index].toInt() and 0xFF
            if (length == 0 || index + length >= advertisementBytes.size) break
            val type = advertisementBytes[index + 1].toInt() and 0xFF
            if (type == 0xFF) {
                val data = advertisementBytes.copyOfRange(index + 2, index + 1 + length)
                tryParseManufacturerData(data)?.let { return it }
            }
            index += length + 1
        }
        return null
    }

    /**
     * Bestes-Wissen-Parsing der Herstellerdaten (nicht offiziell dokumentiert!): 2 Byte
     * Hersteller-ID gefolgt von je 2 Byte Temperatur (vorzeichenbehaftet, Little-Endian,
     * durch 10) und Luftfeuchte (Little-Endian, durch 10) - verbreitetes Layout ähnlicher
     * "ThermoBeacon"-kompatibler Klon-Sensoren. Falls die Werte nach dem ersten Pull
     * unplausibel wirken, hier die Byte-Offsets/den Faktor korrigieren.
     */
    private fun tryParseManufacturerData(data: ByteArray): BleReading? {
        if (data.size < 6) return null
        val tempRawUnsigned = ((data[3].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val tempRaw = if (tempRawUnsigned > 32767) tempRawUnsigned - 65536 else tempRawUnsigned
        val humidityRaw = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val temperature = tempRaw / 10.0
        val humidity = humidityRaw / 10.0
        if (temperature !in -40.0..60.0 || humidity !in 0.0..100.0) return null
        return BleReading(temperature, humidity)
    }
}
