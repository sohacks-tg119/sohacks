package com.hackerman.sohacksrev2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

/**
 * Reine BLE-Transportschicht.
 *
 * Verantwortlich fuer: Verbindungsaufbau, GATT-Callback, Auffinden der
 * Write-/Notify-Charakteristik, Notifications, sowie die TX-/RX-Verschluesselung.
 *
 * WICHTIG: Der Manager kennt KEINE Protokoll-Semantik (Session-Token,
 * Dynamic-Secret, Telemetrie). Er liefert entschluesselte Rohdaten per
 * [Listener] nach oben. Damit bleibt exakt das gleiche gesendet/ausgelesen wie
 * zuvor – die Aufteilung ist rein strukturell.
 *
 * Alle Listener-Callbacks werden auf den Main-Thread gepostet (wie zuvor
 * `runOnUiThread` in der Activity), sodass die UI-Schicht LiveData sicher
 * aktualisieren kann.
 */
@SuppressLint("MissingPermission")
class ScooterBleManager(context: Context) {

    interface Listener {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onProfileResolved(writeFound: Boolean, notifyFound: Boolean)
        fun onReadyForStartup()
        fun onPlainDataReceived(plainBytes: ByteArray, plainHex: String)
    }

    var listener: Listener? = null

    /** Aktuelles Modell – liefert BLE-Profil (UUIDs / AES-Keys) und wird von aussen gesetzt. */
    var model: ScooterModel = ScooterCommandCatalog.defaultModel

    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val encryptedRxBuffer = mutableListOf<Byte>()

    val isConnected: Boolean get() = bluetoothGatt != null

    /** Startet den Verbindungsaufbau. Gibt false zurueck, wenn die Adresse ungueltig ist. */
    fun connect(address: String): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return try {
            val device = adapter.getRemoteDevice(address)
            notifyState(ConnectionState.CONNECTING)
            bluetoothGatt = device.connectGatt(appContext, false, gattCallback)
            Log.d(TAG_BLE, "Connect to $address initiated")
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG_BLE, "Invalid device address: $address")
            false
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeChar = null
        notifyChar = null
        encryptedRxBuffer.clear()
    }

    /**
     * Sendet einen (unverschluesselten) Hex-String. Die Transport-Verschluesselung
     * wird – falls fuer das Modell konfiguriert – hier angewendet. Der Klartext
     * entspricht exakt dem, was zuvor gesendet wurde.
     */
    fun sendRawHex(hex: String): SendResult {
        val writeCharacteristic = writeChar ?: return SendResult.NotConnected
        return try {
            val cleanedHex = HexCodec.normalize(hex)
            val txBytes = encodeTxBytes(cleanedHex)
            writeCharacteristic.writeType =
                if (writeCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
            writeCharacteristic.value = txBytes
            val accepted = bluetoothGatt?.writeCharacteristic(writeCharacteristic) == true
            Log.d(TAG_BLE, "TX plain: $cleanedHex")
            Log.d(TAG_BLE, "TX accepted: $accepted")
            SendResult.Sent
        } catch (e: IllegalArgumentException) {
            SendResult.InvalidHex(e.message)
        } catch (e: Exception) {
            SendResult.Error(e.message)
        }
    }

    private fun encodeTxBytes(cleanedHex: String): ByteArray {
        val aesKey = model.bleProfile.txAesKey
        return if (aesKey == null) {
            HexCodec.toByteArray(cleanedHex)
        } else {
            ScooterTransportCrypto.encryptHexToBytes(cleanedHex, aesKey)
        }
    }

    private fun decodeRxBytes(bytes: ByteArray): ByteArray? {
        val aesKey = model.bleProfile.rxAesKey ?: return bytes

        encryptedRxBuffer += bytes.toList()
        if (encryptedRxBuffer.size % AES_BLOCK_SIZE != 0) {
            Log.w(TAG_BLE, "AES RX buffered length=${encryptedRxBuffer.size}; waiting for full block")
            return null
        }

        return try {
            val decrypted = ScooterTransportCrypto.decryptBytes(encryptedRxBuffer.toByteArray(), aesKey)
            encryptedRxBuffer.clear()
            val unpadded = decrypted
                .dropLastWhile { it == 0.toByte() }
                .toByteArray()
            Log.d(TAG_BLE, "RX decrypted bytes=${decrypted.size}, unpadded=${unpadded.size}")
            unpadded
        } catch (e: Exception) {
            encryptedRxBuffer.clear()
            Log.e(TAG_BLE, "RX decrypt failed: ${e.message}")
            null
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                notifyState(ConnectionState.CONNECTED)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                encryptedRxBuffer.clear()
                writeChar = null
                notifyChar = null
                notifyState(ConnectionState.DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            writeChar = findProfileCharacteristic(gatt, model.bleProfile.writeUuid)
            notifyChar = findProfileCharacteristic(gatt, model.bleProfile.notifyUuid)

            val writeFound = writeChar != null
            val notifyFound = notifyChar != null
            mainHandler.post { listener?.onProfileResolved(writeFound, notifyFound) }

            if (writeFound && notifyFound) enableNotifications(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == UUID.fromString(CCCD_UUID)) {
                mainHandler.post { listener?.onReadyForStartup() }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val rawData = characteristic.value ?: return
            val plainData = decodeRxBytes(rawData) ?: return
            val rawHex = rawData.joinToString("") { "%02X".format(it) }
            val plainHex = plainData.joinToString("") { "%02X".format(it) }
            Log.d(TAG_BLE, "RX raw: $rawHex")
            Log.d(TAG_BLE, "RX plain: $plainHex")
            mainHandler.post { listener?.onPlainDataReceived(plainData, plainHex) }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        val characteristic = notifyChar ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(UUID.fromString(CCCD_UUID)) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(cccd)
    }

    private fun findProfileCharacteristic(gatt: BluetoothGatt, characteristicUuid: String): BluetoothGattCharacteristic? {
        val service = gatt.getService(UUID.fromString(model.bleProfile.serviceUuid)) ?: return null
        return service.getCharacteristic(UUID.fromString(characteristicUuid))
    }

    private fun notifyState(state: ConnectionState) {
        mainHandler.post { listener?.onConnectionStateChanged(state) }
    }

    companion object {
        private const val TAG_BLE = "BLE"
        private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val AES_BLOCK_SIZE = 16
    }
}
