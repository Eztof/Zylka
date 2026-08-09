package com.oliver.zylka.data.plants

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/** Ein in der Nähe gefundenes BLE-Gerät, zum Anlegen eines neuen [Sensor]s ohne die
 * MAC-Adresse von Hand abzutippen. */
data class DiscoveredDevice(val macAddress: String, val name: String?, val rssi: Int)

/** Eine über BLE ausgelesene Momentaufnahme von Temperatur/Feuchte. */
data class BleReading(val temperatureC: Double, val humidityPercent: Double)

/** Ein rohes BLE-Paket für die Diagnose (siehe [SensorBleScanner.scanRawFlow]/[SensorBleScanner.observeGattFlow]):
 * entweder ein Advertisement ([rssi] gesetzt) oder eine GATT-Notification/-Read-Antwort
 * ([rssi] null, [name] dann z. B. "GATT notify: 2b10") - unabhängig davon, ob sich daraus ein
 * TP357-Messwert erkennen lässt, damit sich neue Geräte per Hex-Dump identifizieren und ihr
 * tatsächliches Byte-Format ableiten lässt. */
data class RawScanResult(
    val macAddress: String,
    val name: String?,
    val rssi: Int?,
    val rawBytesHex: String,
    val extraLines: List<String>,
    val epochMillis: Long,
)

/**
 * Liest ThermoPro TP357-Bluetooth-Thermo-Hygrometer aus. Manche Varianten senden
 * Temperatur/Feuchte fortlaufend im BLE-Advertisement (keine Verbindung nötig), andere - wie
 * offenbar dieses Gerät - nur über eine aktive GATT-Verbindung mit Notification-Abo. Deshalb
 * probiert [readSensor] zuerst den GATT-Weg (verbinden, alle Notify-/Read-fähigen
 * Characteristics abhören/lesen) und fällt erst danach auf passives Advertisement-Scannen
 * zurück.
 *
 * **Wichtig:** Beide Byte-Formate ([parseGattNotification], [parseTp357]) sind nicht offiziell
 * dokumentiert und unverifiziert - [parseGattNotification] übernimmt eine Vermutung aus einem
 * Byte-Layout, das für ein anderes (ThermoPro-ähnliches) Projekt beobachtet wurde, ohne
 * Garantie, dass es für dieses Gerät stimmt. Nach dem ersten erfolgreichen Pull unbedingt gegen
 * die Werte in der offiziellen ThermoPro-App prüfen. Unplausible Werte (außerhalb -40..85 °C
 * bzw. 0..100 %) werden bewusst verworfen (null) statt falsche Daten zu liefern. Solange kein
 * Format zuverlässig passt, hilft [scanRawFlow]/[observeGattFlow] (über `SensorDiagnosticActivity`)
 * beim Ableiten des tatsächlichen Layouts.
 */
class SensorBleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** Sucht gezielt nach einem bekannten Sensor (per MAC-Adresse) und liefert dessen aktuellste
     * Messung: erst per GATT-Verbindung, bei Misserfolg per passivem Advertisement-Scan - oder
     * null bei Timeout/keinem Empfang/unplausiblen Werten in beiden Fällen. */
    suspend fun readSensor(
        macAddress: String,
        gattTimeoutMillis: Long = 15_000,
        advertisementTimeoutMillis: Long = 8_000,
    ): BleReading? {
        if (!hasPermissions()) return null
        return readSensorGatt(macAddress, gattTimeoutMillis) ?: readSensorAdvertisement(macAddress, advertisementTimeoutMillis)
    }

    /** Verbindet per GATT, abonniert alle Notify-/Indicate-fähigen Characteristics und liest
     * alle Read-fähigen einmalig direkt aus; der erste per [parseGattNotification] plausible
     * Wert gewinnt. Trennt die Verbindung danach (oder bei Timeout/Abbruch) wieder. */
    @SuppressLint("MissingPermission")
    private suspend fun readSensorGatt(macAddress: String, timeoutMillis: Long): BleReading? {
        val adapter = bluetoothAdapter?.takeIf { it.isEnabled } ?: return null
        val device = adapter.getRemoteDevice(macAddress)
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                var gatt: BluetoothGatt? = null
                fun finish(reading: BleReading?) {
                    if (cont.isActive) cont.resume(reading)
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                }
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            finish(null)
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            finish(null)
                            return
                        }
                        subscribeToAllNotifyCharacteristics(g)
                    }

                    override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                        parseGattNotification(value)?.let { finish(it) }
                    }

                    override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) parseGattNotification(value)?.let { finish(it) }
                    }
                }
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                cont.invokeOnCancellation {
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                }
            }
        }
    }

    /** Rohdaten-Variante von [readSensorGatt] für die Diagnose (`SensorDiagnosticActivity`):
     * verbindet per GATT und liefert **jede** Notification/Read-Antwort jeder Characteristic als
     * Hex-Dump, unabhängig davon, ob [parseGattNotification] sie erkennt - läuft, solange der
     * Flow gesammelt wird, trennt beim Verlassen automatisch wieder. */
    @SuppressLint("MissingPermission")
    fun observeGattFlow(macAddress: String): Flow<RawScanResult> = callbackFlow {
        if (!hasPermissions()) {
            close(IllegalStateException("Bluetooth-Berechtigungen fehlen"))
            return@callbackFlow
        }
        val adapter = bluetoothAdapter?.takeIf { it.isEnabled }
        if (adapter == null) {
            close(IllegalStateException("Bluetooth ist ausgeschaltet"))
            return@callbackFlow
        }
        val device = adapter.getRemoteDevice(macAddress)
        var gatt: BluetoothGatt? = null
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> close(IllegalStateException("GATT-Verbindung getrennt"))
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    close(IllegalStateException("GATT-Service-Suche fehlgeschlagen (Code $status)"))
                    return
                }
                subscribeToAllNotifyCharacteristics(g)
                for (service in g.services) {
                    for (characteristic in service.characteristics) {
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                            g.readCharacteristic(characteristic)
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                trySend(characteristic.toRawScanResult(macAddress, value, "notify"))
            }

            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) trySend(characteristic.toRawScanResult(macAddress, value, "read"))
            }
        }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToAllNotifyCharacteristics(gatt: BluetoothGatt) {
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                val props = characteristic.properties
                if (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) continue
                gatt.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic.getDescriptor(CCCD_UUID) ?: continue
                val enableValue = if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                gatt.writeDescriptor(cccd, enableValue)
            }
        }
    }

    private fun BluetoothGattCharacteristic.toRawScanResult(macAddress: String, value: ByteArray, kind: String): RawScanResult =
        RawScanResult(
            macAddress = macAddress,
            name = "GATT $kind: ${shortUuid(uuid)}",
            rssi = null,
            rawBytesHex = value.toHexString(),
            extraLines = listOf("Service: ${service?.uuid}", "Characteristic: $uuid"),
            epochMillis = System.currentTimeMillis(),
        )

    private fun shortUuid(uuid: UUID): String = uuid.toString().substringBefore('-').trimStart('0').ifEmpty { "0" }

    /**
     * Bestes-Wissen-Parsing einer GATT-Notification/-Read-Antwort (Byte-Layout aus einem
     * anderen, ThermoPro-ähnlichen Projekt übernommen, NICHT für dieses Gerät verifiziert!):
     * Byte 0 Response-Typ (z. B. 0xC2), Byte 1-2 unbekannt, Byte 3-4 Temperatur×10 (uint16
     * Little-Endian), Byte 5 Feuchte in %, Byte 6 unbekannt. Passt der erste Pull nicht zu den
     * echten Werten, hier die Offsets anhand eines per `observeGattFlow` gesammelten Hex-Dumps
     * korrigieren.
     */
    private fun parseGattNotification(data: ByteArray): BleReading? {
        if (data.size < 6) return null
        val tempRaw = ((data[4].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val temperature = tempRaw / 10.0
        val humidity = (data[5].toInt() and 0xFF).toDouble()
        if (temperature !in -40.0..85.0 || humidity !in 0.0..100.0) return null
        return BleReading(temperature, humidity)
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

    /** Sucht gezielt (Advertisement-Filter auf [macAddress]) und versucht, [parseTp357] auf das
     * erste Werbepaket dieses Geräts anzuwenden - Fallback von [readSensor], falls die
     * GATT-Verbindung keinen plausiblen Wert liefert. */
    @SuppressLint("MissingPermission")
    private suspend fun readSensorAdvertisement(macAddress: String, timeoutMillis: Long): BleReading? {
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

    /**
     * Ungefilterte Dauersuche für die Diagnose (`SensorDiagnosticActivity`): läuft, solange der
     * Flow gesammelt wird, und liefert für **jedes** empfangene Werbepaket den vollen Hex-Dump
     * - auch von Geräten, die [parseTp357] nicht erkennt.
     */
    @SuppressLint("MissingPermission")
    fun scanRawFlow(): Flow<RawScanResult> = callbackFlow {
        if (!hasPermissions()) {
            close(IllegalStateException("Bluetooth-Berechtigungen fehlen"))
            return@callbackFlow
        }
        val scanner = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth ist ausgeschaltet"))
            return@callbackFlow
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toRawScanResult())
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE-Scan fehlgeschlagen (Code $errorCode)"))
            }
        }
        scanner.startScan(emptyList(), settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    private fun ScanResult.toRawScanResult(): RawScanResult {
        val record = scanRecord
        val manufacturerData = record?.manufacturerSpecificData?.let { sparse ->
            (0 until sparse.size()).map { i ->
                "${"%04X".format(sparse.keyAt(i))}: ${sparse.valueAt(i).toHexString()}"
            }
        }.orEmpty()
        return RawScanResult(
            macAddress = device.address,
            name = record?.deviceName ?: device.name,
            rssi = rssi,
            rawBytesHex = record?.bytes?.toHexString().orEmpty(),
            extraLines = manufacturerData,
            epochMillis = System.currentTimeMillis(),
        )
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

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

    private companion object {
        /** Client Characteristic Configuration Descriptor - Standard-UUID zum Abonnieren von
         * Notify/Indicate auf einer beliebigen Characteristic. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
