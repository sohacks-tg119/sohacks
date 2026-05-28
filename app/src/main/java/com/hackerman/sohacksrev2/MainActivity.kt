package com.hackerman.sohacksrev2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private lateinit var btnOpenModelList: MaterialButton
    private lateinit var spinnerConnection: Spinner
    private lateinit var btnECO: MaterialButton
    private lateinit var btnNormal: MaterialButton
    private lateinit var btnSport: MaterialButton
    private lateinit var btnDev: MaterialButton
    private lateinit var btnLock: MaterialButton
    private lateinit var btnUnlock: MaterialButton
    private lateinit var speedButtons: Map<Int, MaterialButton>

    private lateinit var tvHeaderSpeed: TextView
    private lateinit var imgLightOff: ImageView
    private lateinit var imgLightOn: ImageView
    private lateinit var sliderSpeedModifier: Slider
    private lateinit var tvSpeedModifier: TextView
    private lateinit var cardAdvanced: View
    private lateinit var switchAdvanced: Switch
    private lateinit var advancedContainer: View
    private lateinit var layoutAdvancedContent: LinearLayout
    private lateinit var spinnerModes: Spinner
    private lateinit var txtCmdHex: EditText
    private lateinit var btnSendHex: MaterialButton
    private lateinit var tvBleOutput: TextView

    private lateinit var prefs: SharedPreferences
    private var selectedModel: ScooterModel = ScooterCommandCatalog.defaultModel
    private var startupCompleted = false
    private var autoReconnectAttempted = false
    private var suppressConnectionSelection = false
    private var scooterConnected = false
    private val telemetryFrameBuffer = ScooterTelemetryFrameBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedModel = ScooterCommandCatalog.findModel(prefs.getString(KEY_MODEL_ID, null))

        bindUi()
        resetTelemetryHeader()
        setupConnectionDropdown()
        setupCommandButtons()
        setupAdvancedControls()
        setupSpeedControls()
        applySelectedModel()
        applyAdvancedPreference()
        runInitialSetup()
    }

    private fun bindUi() {
        btnOpenModelList = findViewById(R.id.btnOpenModelList)
        spinnerConnection = findViewById(R.id.spinnerConnection)
        btnECO = findViewById(R.id.btnECO)
        btnNormal = findViewById(R.id.btnNormal)
        btnSport = findViewById(R.id.btnSport)
        btnDev = findViewById(R.id.btnDev)
        btnLock = findViewById(R.id.btnLock)
        btnUnlock = findViewById(R.id.btnUnlock)
        speedButtons = mapOf(
            8 to findViewById(R.id.btn8kmh),
            15 to findViewById(R.id.btn15kmh),
            20 to findViewById(R.id.btn20kmh),
            25 to findViewById(R.id.btn25kmh),
            30 to findViewById(R.id.btn30kmh)
        )

        tvHeaderSpeed = findViewById(R.id.tvHeaderSpeed)
        imgLightOff = findViewById(R.id.imgLightOff)
        imgLightOn = findViewById(R.id.imgLightOn)
        sliderSpeedModifier = findViewById(R.id.sliderSpeedModifier)
        tvSpeedModifier = findViewById(R.id.tvSpeedModifier)
        cardAdvanced = findViewById(R.id.cardAdvanced)
        switchAdvanced = findViewById(R.id.switchAdvanced)
        advancedContainer = findViewById(R.id.advancedContainer)
        layoutAdvancedContent = findViewById(R.id.layoutAdvancedContent)
        spinnerModes = findViewById(R.id.advanced_dropdown_1_to_254)
        txtCmdHex = findViewById(R.id.txt_cmd_hex)
        btnSendHex = findViewById(R.id.btnSendHex)
        tvBleOutput = findViewById(R.id.tvBleOutput)
    }

    private fun runInitialSetup() {
        if (!prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) {
            showDisclaimer()
            return
        }

        if (!hasBlePermissions()) {
            requestBlePermissions()
            return
        }

        val savedModelId = prefs.getString(KEY_MODEL_ID, null)
        if (savedModelId == null) {
            openModelSelection(required = true)
            return
        }

        selectedModel = ScooterCommandCatalog.findModel(savedModelId)
        applySelectedModel()
        applyAdvancedPreference()
        startupCompleted = true
        if (!autoReconnectAttempted) {
            autoReconnectAttempted = true
            autoReconnectLastDevice()
        }
    }

    private fun showDisclaimer() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Wichtiger Hinweis")
            .setMessage(
                "Die Nutzung dieser App kann das Verhalten deines Scooters verändern. " +
                    "Tuning und veränderte Geschwindigkeiten können im Straßenverkehr illegal sein und zu Bußgeldern, " +
                    "Versicherungsverlust oder Gefährdungen führen.\n\n" +
                    "Diese App wird ohne Gewähr bereitgestellt. Der Entwickler übernimmt keine Haftung für Schäden, " +
                    "Rechtsfolgen oder Fehlfunktionen. Nutze die App ausschließlich auf eigene Verantwortung und nur dort, " +
                    "wo es rechtlich zulässig ist."
            )
            .setPositiveButton("Ich akzeptiere das Risiko") { _, _ ->
                prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
                runInitialSetup()
            }
            .setNegativeButton("Abbrechen") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun hasBlePermissions(): Boolean = requiredBlePermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBlePermissions() {
        ActivityCompat.requestPermissions(this, requiredBlePermissions().toTypedArray(), REQ_BLE_PERMS)
    }

    private fun requiredBlePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_BLE_PERMS) return

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            runInitialSetup()
        } else {
            Toast.makeText(this, "Berechtigungen fehlen für BLE-Verbindung", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupConnectionDropdown() {
        CONNECTION_ACTIONS[CONNECTION_IDLE_INDEX] = "Verbindung: Nicht verbunden"
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, CONNECTION_ACTIONS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerConnection.adapter = adapter
        spinnerConnection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressConnectionSelection || position == CONNECTION_IDLE_INDEX) return

                when (position) {
                    CONNECTION_CONNECT_INDEX -> {
                        if (bluetoothGatt == null) {
                            connectLastDeviceOrPick()
                        } else {
                            Toast.makeText(this@MainActivity, "Bereits verbunden", Toast.LENGTH_SHORT).show()
                        }
                    }
                    CONNECTION_CHANGE_INDEX -> {
                        disconnect(showToast = false)
                        pickDevice()
                    }
                    CONNECTION_DISCONNECT_INDEX -> disconnect()
                }
                resetConnectionDropdown()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun resetConnectionDropdown() {
        suppressConnectionSelection = true
        spinnerConnection.setSelection(CONNECTION_IDLE_INDEX, false)
        suppressConnectionSelection = false
    }

    private fun updateConnectionDropdownLabel(label: String) {
        CONNECTION_ACTIONS[CONNECTION_IDLE_INDEX] = "Verbindung: $label"
        (spinnerConnection.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        resetConnectionDropdown()
    }

    private fun setupCommandButtons() {
        btnOpenModelList.setOnClickListener { openModelSelection(required = false) }
        btnECO.setOnClickListener { sendHex(selectedModel.commands.eco) }
        btnNormal.setOnClickListener { sendHex(selectedModel.commands.normal) }
        btnSport.setOnClickListener { sendHex(selectedModel.commands.sport) }
        btnDev.setOnClickListener { sendHex(selectedModel.commands.dev) }
        btnLock.setOnClickListener { sendHex(selectedModel.commands.lock) }
        btnUnlock.setOnClickListener { sendHex(selectedModel.commands.unlock) }

        speedButtons.forEach { (speed, button) ->
            button.setOnClickListener { sendSpeed(speed) }
        }
    }

    private fun setupAdvancedControls() {
        switchAdvanced.setOnCheckedChangeListener { _, isChecked ->
            advancedContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            advancedContainer.requestLayout()
        }

        spinnerModes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!switchAdvanced.isChecked) return

                val mode = position + 1
                val command = selectedModel.advancedModeCommand(mode)
                if (command == null) {
                    Toast.makeText(this@MainActivity, "Kein Kommando für Mode $mode", Toast.LENGTH_SHORT).show()
                    return
                }

                sendHex(command)
                Toast.makeText(this@MainActivity, "Mode $mode gesendet", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnSendHex.setOnClickListener {
            val hex = txtCmdHex.text.toString().trim()
            if (hex.isEmpty()) {
                Toast.makeText(this, "Bitte Hex eingeben", Toast.LENGTH_SHORT).show()
            } else {
                sendHex(hex)
            }
        }
    }

    private fun setupSpeedControls() {
        sliderSpeedModifier.addOnChangeListener { _, value, fromUser ->
            val speed = value.toInt().coerceIn(
                selectedModel.supportedSpeeds.first(),
                selectedModel.supportedSpeeds.last()
            )
            tvSpeedModifier.text = "Speed Modifier: $speed km/h"
            if (fromUser) sendSpeed(speed)
        }
    }

    private fun applySelectedModel() {
        val speeds = selectedModel.supportedSpeeds
        sliderSpeedModifier.valueFrom = speeds.first().toFloat()
        sliderSpeedModifier.valueTo = speeds.last().toFloat()
        sliderSpeedModifier.stepSize = 1f
        sliderSpeedModifier.value = sliderSpeedModifier.value
            .toInt()
            .coerceIn(speeds.first(), speeds.last())
            .toFloat()
        tvSpeedModifier.text = "Speed Modifier: ${sliderSpeedModifier.value.toInt()} km/h"

        speedButtons.forEach { (speed, button) ->
            val enabled = selectedModel.speedCommand(speed) != null
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.45f
        }

        val modeLabels = (1..selectedModel.maxAdvancedMode).map { "Mode $it" }
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeLabels)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModes.adapter = modeAdapter
    }

    private fun applyAdvancedPreference() {
        val enabled = prefs.getBoolean(KEY_ADVANCED_OPTIONS, false)
        cardAdvanced.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            switchAdvanced.isChecked = false
            advancedContainer.visibility = View.GONE
        }
    }

    private fun openModelSelection(required: Boolean) {
        val intent = Intent(this, ModelSelectionActivity::class.java)
        intent.putExtra(EXTRA_MODEL_REQUIRED, required)
        startActivityForResult(intent, REQ_PICK_MODEL)
    }

    private fun autoReconnectLastDevice() {
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null) ?: return
        connectToAddress(address)
    }

    private fun connectLastDeviceOrPick() {
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null)
        if (address == null) {
            pickDevice()
        } else {
            connectToAddress(address)
        }
    }

    private fun connectToAddress(address: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            val device = adapter.getRemoteDevice(address)
            updateConnectionDropdownLabel("Verbinde...")
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
            Log.d(TAG_BLE, "Connect to $address initiated")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG_BLE, "Invalid device address: $address")
            pickDevice()
        }
    }

    private fun pickDevice() {
        val intent = Intent(this, DeviceSelectionActivity1::class.java)
        startActivityForResult(intent, REQ_PICK_DEVICE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_PICK_MODEL) {
            val modelId = data?.getStringExtra(EXTRA_MODEL_ID) ?: prefs.getString(KEY_MODEL_ID, null)
            if (resultCode == Activity.RESULT_OK && modelId != null) {
                selectedModel = ScooterCommandCatalog.findModel(modelId)
                prefs.edit().putString(KEY_MODEL_ID, selectedModel.id).apply()
                applySelectedModel()
                applyAdvancedPreference()
                if (!startupCompleted) runInitialSetup()
            } else if (startupCompleted) {
                applyAdvancedPreference()
            } else if (!startupCompleted) {
                finish()
            }
            return
        }

        if (requestCode != REQ_PICK_DEVICE || resultCode != Activity.RESULT_OK) return

        val address = data?.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return
        prefs.edit().putString(KEY_DEVICE_ADDRESS, address).apply()
        val device = bluetoothAdapter?.getRemoteDevice(address)
        bluetoothGatt = device?.connectGatt(this, false, gattCallback)
        updateConnectionDropdownLabel("Verbinde...")
    }

    private fun disconnect(showToast: Boolean = true) {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeChar = null
        notifyChar = null
        scooterConnected = false
        telemetryFrameBuffer.clear()
        resetTelemetryHeader()
        updateConnectionDropdownLabel("Nicht verbunden")
        if (showToast) Toast.makeText(this, "Getrennt", Toast.LENGTH_SHORT).show()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scooterConnected = true
                runOnUiThread { updateConnectionDropdownLabel("Verbunden") }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                scooterConnected = false
                telemetryFrameBuffer.clear()
                runOnUiThread {
                    updateConnectionDropdownLabel("Nicht verbunden")
                    resetTelemetryHeader()
                }
                writeChar = null
                notifyChar = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            var foundWrite: BluetoothGattCharacteristic? = null
            var foundNotify: BluetoothGattCharacteristic? = null
            outer@ for (service in gatt.services) {
                var localWrite: BluetoothGattCharacteristic? = null
                var localNotify: BluetoothGattCharacteristic? = null
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    if (
                        localWrite == null &&
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                            props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                    ) {
                        localWrite = characteristic
                    }
                    if (localNotify == null && props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        localNotify = characteristic
                    }
                    if (localWrite != null && localNotify != null) {
                        foundWrite = localWrite
                        foundNotify = localNotify
                        break@outer
                    }
                }
            }

            writeChar = foundWrite
            notifyChar = foundNotify
            enableNotifications(gatt)

            runOnUiThread {
                val message = if (writeChar != null && notifyChar != null) {
                    "WRITE/NOTIFY-Paar gefunden"
                } else {
                    "Kein WRITE/NOTIFY-Paar gefunden"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            val hex = data.joinToString("") { "%02X".format(it) }
            Log.d(TAG_BLE, "RX: $hex")
            val telemetry = if (scooterConnected) telemetryFrameBuffer.append(data) else null
            runOnUiThread {
                tvBleOutput.text = hex
                if (telemetry != null) updateTelemetryHeader(telemetry)
            }
        }
    }

    private fun updateTelemetryHeader(telemetry: ScooterTelemetry) {
        tvHeaderSpeed.text = telemetry.formattedSpeed
        imgLightOn.visibility = if (telemetry.lightOn) View.VISIBLE else View.GONE
        imgLightOff.visibility = if (telemetry.lightOn) View.GONE else View.VISIBLE
    }

    private fun resetTelemetryHeader() {
        tvHeaderSpeed.text = "00.0 km/h"
        imgLightOn.visibility = View.GONE
        imgLightOff.visibility = View.VISIBLE
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        val characteristic = notifyChar ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(UUID.fromString(CCCD_UUID)) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(cccd)
    }

    private fun sendSpeed(speed: Int) {
        val command = selectedModel.speedCommand(speed)
        if (command == null) {
            Toast.makeText(this, "${selectedModel.displayName} unterstützt $speed km/h nicht", Toast.LENGTH_SHORT).show()
            return
        }
        sendHex(command)
    }

    private fun sendHex(hex: String) {
        val writeCharacteristic = writeChar ?: run {
            Toast.makeText(this, "Nicht verbunden oder kein Write-Char", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cleanedHex = HexCodec.normalize(hex)
            writeCharacteristic.value = HexCodec.toByteArray(cleanedHex)
            bluetoothGatt?.writeCharacteristic(writeCharacteristic)
            Log.d(TAG_BLE, "TX: $cleanedHex")
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message ?: "Ungültiges Hex", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Senden fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val CONNECTION_ACTIONS = mutableListOf("Verbindung: Nicht verbunden", "Verbinden", "Gerät wechseln", "Trennen")
        private const val CONNECTION_IDLE_INDEX = 0
        private const val CONNECTION_CONNECT_INDEX = 1
        private const val CONNECTION_CHANGE_INDEX = 2
        private const val CONNECTION_DISCONNECT_INDEX = 3

        private const val PREFS_NAME = "BLE_Prefs"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimerAccepted"
        private const val KEY_ADVANCED_OPTIONS = "advanced_options_enabled"
        private const val EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        private const val EXTRA_MODEL_ID = "MODEL_ID"
        private const val EXTRA_MODEL_REQUIRED = "MODEL_REQUIRED"
        private const val REQ_BLE_PERMS = 2001
        private const val REQ_PICK_DEVICE = 2002
        private const val REQ_PICK_MODEL = 2003
        private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val TAG_BLE = "BLE"
    }
}
