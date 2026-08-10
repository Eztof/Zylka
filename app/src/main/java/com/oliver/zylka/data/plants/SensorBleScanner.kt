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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.UUID
import kotlin.coroutines.resume

/** Ein in der Nähe gefundenes BLE-Gerät, zum Anlegen eines neuen [Sensor]s ohne die
 * MAC-Adresse von Hand abzutippen. */
data class DiscoveredDevice(val macAddress: String, val name: String?, val rssi: Int)

/** Eine über BLE ausgelesene Momentaufnahme von Temperatur/Feuchte. */
data class BleReading(val temperatureC: Double, val humidityPercent: Double)

/** Ein einzelner Datensatz aus der im Gerät gespeicherten Historie ([SensorBleScanner.readHistory])
 * - ohne eigenen Zeitstempel, siehe dort. [index] ist die Position in der vom Gerät gelieferten
 * Reihenfolge (0 = neuester Datensatz), NICHT die Position in der zurückgegebenen Liste - falls
 * einzelne Datensätze wegen unplausibler Werte übersprungen wurden (siehe [readHistory]), bleiben
 * dadurch beim Rekonstruieren der Zeitstempel die echten Lücken erhalten statt sich zu verschieben. */
data class HistoryReading(val index: Int, val temperatureC: Double, val humidityPercent: Double)

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
 * **Wichtig:** Beide Byte-Formate sind reverse-engineered, nicht offiziell dokumentiert. Für
 * [parseGattNotification] liegt inzwischen ein echter Hex-Dump eines TP357S vor (zwei
 * Notifications im Abstand von ~7 Minuten: `C2 00 00 10 01 1F 2C` → 27,2 °C/31 %, danach
 * `C2 00 00 11 01 1F 2C` → 27,3 °C/31 % - Temperatur/Feuchte plausibel verändert, Byte 6
 * (`0x2C`, vermutlich Batterie-%) über die 7 Minuten unverändert), das Format gilt damit für
 * dieses Gerät als bestätigt. [parseTp357] (Advertisement-Fallback) bleibt unverifiziert - bei
 * diesem Gerät liefert das Advertisement ohnehin keine sinnvollen Werte, nur der GATT-Weg
 * funktioniert. Unplausible Werte (außerhalb -40..85 °C bzw. 0..100 %) werden bewusst verworfen
 * (null) statt falsche Daten zu liefern. Bei einem anderen Gerät/Format hilft
 * [scanRawFlow]/[observeGattFlow] (über `SensorDiagnosticActivity`) beim Ableiten des
 * tatsächlichen Layouts.
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
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
                cont.invokeOnCancellation {
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                }
            }
        }
    }

    /**
     * Lädt die im Gerät gespeicherte Messhistorie per GATT (reverse-engineertes Protokoll für
     * die TP357S-Variante, siehe github.com/giovannipizzi/pytp357s/blob/main/PROTOCOL.md - dort
     * dokumentiert an Service `...1910`/Write-Characteristic `...2b11`/Notify-Characteristic
     * `...2b10`, die exakt zu diesem Gerät passen): meldet dem Sensor die aktuelle Uhrzeit,
     * schickt die vom Protokoll vorgesehene Drei-Kommando-Sequenz (Session-Init, Offset,
     * Datenanfrage mit [recordCount] als gewünschter Satzzahl) und sammelt die über mehrere
     * Notifications gestückelte Antwort wieder zu einer zusammenhängenden Liste.
     *
     * **Wichtig:** Das Protokoll liefert pro Datensatz **keinen eigenen Zeitstempel** - nur die
     * Reihenfolge (neuester zuerst, siehe [HistoryReading.index]) und den Aufnahme-Abstand, den
     * [HISTORY_RECORD_INTERVAL_MINUTES] als Annahme festhält (im Referenzprojekt ~1 Minute, am
     * TP357S selbst nicht geprüft). Falls am Gerät ein anderes Log-Intervall eingestellt ist,
     * verschieben sich die daraus rekonstruierten Zeitstempel entsprechend.
     *
     * Die Zusammensetzung der über mehrere Notifications gestückelten Antwort erfolgt paketweise
     * (jedes Notification-Paket wird für sich 3-Byte-ausgerichtet, siehe
     * [HistoryAssembly.consumeChunk]) - an zwei vollständigen echten Aufnahmen bestätigt (0 von
     * 32 bzw. 0 von 37 Datensätzen unplausibel). Die Plausibilitätsprüfung (-40..85 °C /
     * 0..100 %) bleibt als Sicherheitsnetz bestehen und überspringt einzelne Datensätze, falls
     * doch einmal ein unerwartetes Paket dazwischenfunkt. Liefert null bei Timeout/fehlenden
     * Characteristics/Verbindungsabbruch.
     */
    @SuppressLint("MissingPermission")
    suspend fun readHistory(macAddress: String, recordCount: Int = 500, timeoutMillis: Long = 25_000): List<HistoryReading>? {
        if (!hasPermissions()) return null
        val adapter = bluetoothAdapter?.takeIf { it.isEnabled } ?: return null
        val device = adapter.getRemoteDevice(macAddress)

        val servicesReady = CompletableDeferred<Boolean>()
        val historyResult = CompletableDeferred<List<HistoryReading>?>()
        var writeCharacteristic: BluetoothGattCharacteristic? = null
        val assembly = HistoryAssembly()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    servicesReady.complete(false)
                    historyResult.complete(null)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    servicesReady.complete(false)
                    return
                }
                val notifyChar = g.services.firstNotNullOfOrNull { it.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID) }
                val writeChar = g.services.firstNotNullOfOrNull { it.getCharacteristic(WRITE_CHARACTERISTIC_UUID) }
                if (notifyChar == null || writeChar == null) {
                    servicesReady.complete(false)
                    return
                }
                writeCharacteristic = writeChar
                g.setCharacteristicNotification(notifyChar, true)
                val cccd = notifyChar.getDescriptor(CCCD_UUID)
                if (cccd != null) g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                servicesReady.complete(true)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid != NOTIFY_CHARACTERISTIC_UUID) return
                if (assembly.consumeChunk(value)) historyResult.complete(assembly.readings)
            }
        }

        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK) ?: return null
        return try {
            withTimeoutOrNull(timeoutMillis) {
                if (servicesReady.await() != true) return@withTimeoutOrNull null
                val writeChar = writeCharacteristic ?: return@withTimeoutOrNull null
                // Manche Vendor-Characteristics erlauben nur "ohne Antwort" schreiben - anhand
                // der tatsächlichen Properties wählen statt es zu erzwingen (sonst schlägt der
                // Schreibzugriff ggf. mit einem Fehlercode fehl, ohne dass wir das hier sehen).
                val writeType = if (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                val now = Calendar.getInstance()
                // Kleine Pausen zwischen den Schreibbefehlen, wie im Referenzprotokoll
                // beobachtet - das Gerät scheint pro Kommando etwas Verarbeitungszeit zu
                // brauchen, statt jeden Schreibzugriff per GATT-Callback zu bestätigen.
                delay(300)
                gatt.writeCharacteristic(writeChar, buildDatetimeSyncCommand(now), writeType)
                delay(200)
                gatt.writeCharacteristic(writeChar, SESSION_INIT_COMMAND, writeType)
                delay(200)
                gatt.writeCharacteristic(writeChar, OFFSET_COMMAND, writeType)
                delay(200)
                gatt.writeCharacteristic(writeChar, buildHistoryRequestCommand(now, recordCount), writeType)
                historyResult.await()
            }
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private fun checksum(bytes: ByteArray): Byte = (bytes.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()

    /** `a5 YY MM DD HH MM SS DOW CS` - setzt die Uhrzeit des Sensors auf [now] (Wochentag
     * 1=Sonntag..7=Samstag, deckt sich mit [Calendar.DAY_OF_WEEK]). */
    private fun buildDatetimeSyncCommand(now: Calendar): ByteArray {
        val body = byteArrayOf(
            0xA5.toByte(),
            (now.get(Calendar.YEAR) - 2000).toByte(),
            (now.get(Calendar.MONTH) + 1).toByte(),
            now.get(Calendar.DAY_OF_MONTH).toByte(),
            now.get(Calendar.HOUR_OF_DAY).toByte(),
            now.get(Calendar.MINUTE).toByte(),
            now.get(Calendar.SECOND).toByte(),
            now.get(Calendar.DAY_OF_WEEK).toByte(),
        )
        return body + checksum(body)
    }

    /** `cc cc 01 09 00 00 00 YY MM DD HH MM SS NL NH CS 66 66` - fordert bis zu [recordCount]
     * Datensätze an (NL/NH = Anzahl, 16 Bit Little-Endian). */
    private fun buildHistoryRequestCommand(now: Calendar, recordCount: Int): ByteArray {
        val body = byteArrayOf(
            0x01, 0x09, 0x00, 0x00, 0x00,
            (now.get(Calendar.YEAR) - 2000).toByte(),
            (now.get(Calendar.MONTH) + 1).toByte(),
            now.get(Calendar.DAY_OF_MONTH).toByte(),
            now.get(Calendar.HOUR_OF_DAY).toByte(),
            now.get(Calendar.MINUTE).toByte(),
            now.get(Calendar.SECOND).toByte(),
            (recordCount and 0xFF).toByte(),
            ((recordCount shr 8) and 0xFF).toByte(),
        )
        return byteArrayOf(0xCC.toByte(), 0xCC.toByte()) + body + checksum(body) + byteArrayOf(0x66, 0x66)
    }

    /**
     * Sammelzustand für eine Historien-Antwort über mehrere Notifications (siehe
     * [HistoryAssembly.consumeChunk]).
     */
    private class HistoryAssembly {
        var frameStarted = false
        var recordIndex = 0
        val readings = mutableListOf<HistoryReading>()
    }

    /**
     * Verarbeitet **ein einzelnes** BLE-Notification-Paket der Historien-Antwort im Rahmen
     * `cc cc 01 [3 Bytes, Bedeutung ungeklärt] 00 [Temp₁₆ Temp₈ Feuchte]… 66 66`.
     *
     * **An zwei vollständigen echten Aufnahmen bestätigt:** Das TP357S richtet die 3-Byte-
     * Datensätze offenbar **pro Notification-Paket neu aus**, statt den Byte-Strom nahtlos über
     * Paketgrenzen hinweg fortzusetzen - 1-2 Restbytes am Ende eines Pakets, die sich nicht mehr
     * zu einem vollen 3-Byte-Datensatz ergänzen, gehören NICHT zum nächsten Paket und werden
     * verworfen (kein Übertrag). Naive durchgehende Verkettung (die ursprüngliche Annahme)
     * erzeugte an genau diesen Paketgrenzen physikalisch unmögliche Werte; mit paketweiser
     * Ausrichtung decodieren beide Aufnahmen vollständig plausibel (0 von 32 bzw. 0 von 37
     * Datensätzen unplausibel). Die 3 Bytes nach `01` im ersten Paket sind weiterhin ungeklärt
     * (ursprünglich als Byte-Länge der Gesamtantwort interpretiert, was sich als falsch erwiesen
     * hat) und werden nur noch als Teil des 7-Byte-Kopfs übersprungen, nicht mehr ausgewertet.
     *
     * @return true, wenn dieses Paket das Rahmenende (`66 66`) enthielt - [readings] ist dann
     * vollständig.
     */
    private fun HistoryAssembly.consumeChunk(chunk: ByteArray): Boolean {
        var body = chunk
        if (!frameStarted) {
            if (body.size < 7 || body[0] != 0xCC.toByte() || body[1] != 0xCC.toByte() || body[2] != 0x01.toByte()) {
                return false // noch kein gültiger Rahmenbeginn - z. B. eine dazwischenfunkende
                // Live-Notification auf derselben Characteristic, siehe [parseGattNotification].
            }
            body = body.copyOfRange(7, body.size)
            frameStarted = true
        }
        var isLast = false
        if (body.size >= 2 && body[body.size - 2] == 0x66.toByte() && body[body.size - 1] == 0x66.toByte()) {
            body = body.copyOfRange(0, body.size - 2)
            isLast = true
        }
        val usableBytes = (body.size / 3) * 3
        var i = 0
        while (i < usableBytes) {
            val tempRawUnsigned = ((body[i + 1].toInt() and 0xFF) shl 8) or (body[i].toInt() and 0xFF)
            val tempRaw = if (tempRawUnsigned > 32767) tempRawUnsigned - 65536 else tempRawUnsigned
            val temperature = tempRaw / 10.0
            val humidity = (body[i + 2].toInt() and 0xFF).toDouble()
            if (temperature in -40.0..85.0 && humidity in 0.0..100.0) {
                readings.add(HistoryReading(recordIndex, temperature, humidity))
            }
            recordIndex++
            i += 3
        }
        return isLast
    }

    /**
     * Rohdaten-Variante von [readSensorGatt] für die Diagnose (`SensorDiagnosticActivity`):
     * verbindet per GATT und liefert **jede** Notification/Read-Antwort jeder Characteristic als
     * Hex-Dump, unabhängig davon, ob [parseGattNotification] sie erkennt - läuft, solange der
     * Flow gesammelt wird, trennt beim Verlassen automatisch wieder.
     *
     * Mit [sendHistoryRequest] wird zusätzlich genau die Kommandosequenz aus [readHistory]
     * geschickt, aber **ungefiltert** protokolliert, was zurückkommt - auch wenn es nicht ins
     * von [readHistory] erwartete Rahmenformat passt. Damit lässt sich sehen, ob das Gerät
     * überhaupt antwortet (falsche Characteristic/Checksumme/Schreibtyp) oder ob "nur" das
     * Rahmenformat von der Erwartung abweicht.
     */
    @SuppressLint("MissingPermission")
    fun observeGattFlow(macAddress: String, sendHistoryRequest: Boolean = false, recordCount: Int = 500): Flow<RawScanResult> = callbackFlow {
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
        var writeCharacteristic: BluetoothGattCharacteristic? = null
        val servicesReady = CompletableDeferred<Boolean>()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        servicesReady.complete(false)
                        close(IllegalStateException("GATT-Verbindung getrennt"))
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    servicesReady.complete(false)
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
                writeCharacteristic = g.services.firstNotNullOfOrNull { it.getCharacteristic(WRITE_CHARACTERISTIC_UUID) }
                servicesReady.complete(true)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                trySend(characteristic.toRawScanResult(macAddress, value, "notify"))
            }

            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) trySend(characteristic.toRawScanResult(macAddress, value, "read"))
            }
        }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK)
        if (sendHistoryRequest) {
            launch {
                if (servicesReady.await() != true) return@launch
                val writeChar = writeCharacteristic ?: return@launch
                val writeType = if (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                val now = Calendar.getInstance()
                delay(300)
                gatt?.writeCharacteristic(writeChar, buildDatetimeSyncCommand(now), writeType)
                delay(200)
                gatt?.writeCharacteristic(writeChar, SESSION_INIT_COMMAND, writeType)
                delay(200)
                gatt?.writeCharacteristic(writeChar, OFFSET_COMMAND, writeType)
                delay(200)
                gatt?.writeCharacteristic(writeChar, buildHistoryRequestCommand(now, recordCount), writeType)
            }
        }
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
     * Parsing einer GATT-Notification/-Read-Antwort: Byte 0 Response-Typ (0xC2), Byte 1-2
     * unbekannt (bislang immer 0x00 0x00), Byte 3-4 Temperatur×10 (uint16 Little-Endian),
     * Byte 5 Feuchte in %, Byte 6 unbekannt (vermutlich Batterie-% - blieb über mehrere Minuten
     * unverändert, während sich Temperatur/Feuchte plausibel bewegten). An einem echten TP357S
     * bestätigt (`C2 00 00 10 01 1F 2C` → 27,2 °C/31 %, wenig später `C2 00 00 11 01 1F 2C` →
     * 27,3 °C/31 %) - für ein anderes Gerät/eine andere Firmware können die Offsets trotzdem
     * abweichen; dann hier anhand eines per `observeGattFlow` gesammelten Hex-Dumps korrigieren.
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

    companion object {
        /** Client Characteristic Configuration Descriptor - Standard-UUID zum Abonnieren von
         * Notify/Indicate auf einer beliebigen Characteristic. */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** TP357S-Vendor-Service samt Notify-/Write-Characteristic für Live-Wert und Historie,
         * siehe github.com/giovannipizzi/pytp357s/blob/main/PROTOCOL.md. */
        private val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b10")
        private val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b11")

        /** `cc cc 02 01 00 00 01 04 66 66` - fester Session-Init-Befehl aus dem Referenzprotokoll,
         * ohne variable Felder. */
        private val SESSION_INIT_COMMAND =
            byteArrayOf(0xCC.toByte(), 0xCC.toByte(), 0x02, 0x01, 0x00, 0x00, 0x01, 0x04, 0x66, 0x66)

        /** `cc cc 04 00 00 00 00 04 66 66` - fester Offset-Befehl (laut Referenzprotokoll wird die
         * Antwort darauf ignoriert, er scheint aber Teil der Handshake-Sequenz zu sein). */
        private val OFFSET_COMMAND =
            byteArrayOf(0xCC.toByte(), 0xCC.toByte(), 0x04, 0x00, 0x00, 0x00, 0x00, 0x04, 0x66, 0x66)

        /** Angenommener Aufnahme-Abstand der Gerätehistorie in Minuten (siehe [readHistory]) -
         * unbestätigte Annahme aus dem Referenzprotokoll, am TP357S selbst nicht geprüft. */
        const val HISTORY_RECORD_INTERVAL_MINUTES = 1L
    }
}
