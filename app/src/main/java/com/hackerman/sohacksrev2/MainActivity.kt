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
import android.widget.CheckBox
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
import java.util.Locale
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
    private lateinit var tvHeaderTelemetryDetails: TextView
    private lateinit var imgLightOff: ImageView
    private lateinit var imgLightOn: ImageView
    private lateinit var sliderSpeedModifier: Slider
    private lateinit var tvSpeedModifier: TextView
    private lateinit var cardAdvanced: View
    private lateinit var switchAdvanced: Switch
    private lateinit var advancedContainer: View
    private lateinit var layoutAdvancedContent: LinearLayout
    private lateinit var spinnerModes: Spinner
    private lateinit var cbMoreSpeed: CheckBox
    private lateinit var tvExtraCommandsLabel: TextView
    private lateinit var extraCommandsContainer: LinearLayout
    private lateinit var txtCmdHex: EditText
    private lateinit var btnSendHex: MaterialButton
    private lateinit var tvBleOutput: TextView

    private lateinit var prefs: SharedPreferences
    private var selectedModel: ScooterModel = ScooterCommandCatalog.defaultModel
    private var startupCompleted = false
    private var autoReconnectAttempted = false
    private var suppressConnectionSelection = false
    private var scooterConnected = false
    private var latestDynamicSecret: Int? = null
    private var sessionToken: String? = null
    private var realtimeStarted = false
    private var latestTelemetry: ScooterTelemetry? = null
    private val encryptedRxBuffer = mutableListOf<Byte>()
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
        tvHeaderTelemetryDetails = findViewById(R.id.tvHeaderTelemetryDetails)
        imgLightOff = findViewById(R.id.imgLightOff)
        imgLightOn = findViewById(R.id.imgLightOn)
        sliderSpeedModifier = findViewById(R.id.sliderSpeedModifier)
        tvSpeedModifier = findViewById(R.id.tvSpeedModifier)
        cardAdvanced = findViewById(R.id.cardAdvanced)
        switchAdvanced = findViewById(R.id.switchAdvanced)
        advancedContainer = findViewById(R.id.advancedContainer)
        layoutAdvancedContent = findViewById(R.id.layoutAdvancedContent)
        spinnerModes = findViewById(R.id.advanced_dropdown_1_to_254)
        cbMoreSpeed = findViewById(R.id.cbMoreSpeed)
        tvExtraCommandsLabel = findViewById(R.id.tvExtraCommandsLabel)
        extraCommandsContainer = findViewById(R.id.extraCommandsContainer)
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
        btnECO.setOnClickListener { sendCatalogCommand(selectedModel.commands.eco, "ECO") }
        btnNormal.setOnClickListener { sendCatalogCommand(selectedModel.commands.normal, "Normal") }
        btnSport.setOnClickListener { sendCatalogCommand(selectedModel.commands.sport, "Sport") }
        btnDev.setOnClickListener { sendCatalogCommand(selectedModel.commands.dev, "Dev") }
        btnLock.setOnClickListener { sendCatalogCommand(selectedModel.commands.lock, "Lock") }
        btnUnlock.setOnClickListener { sendCatalogCommand(selectedModel.commands.unlock, "Unlock") }

        speedButtons.forEach { (speed, button) ->
            button.setOnClickListener { sendSpeed(speed) }
        }
    }

    private fun setupAdvancedControls() {
        cbMoreSpeed.isChecked = prefs.getBoolean(KEY_MORE_SPEED, false)
        cbMoreSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MORE_SPEED, isChecked).apply()
            applySelectedModel()
        }

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
            val speeds = selectedModel.supportedSpeeds
            if (speeds.isEmpty()) return@addOnChangeListener

            val speedRange = currentSpeedRange()
            val speed = value.toInt().coerceIn(speedRange.first, speedRange.last)
            tvSpeedModifier.text = "Speed Modifier: $speed km/h"
            if (fromUser) sendSpeed(speed)
        }
    }

    private fun applySelectedModel() {
        val speeds = selectedModel.supportedSpeeds
        if (speeds.isEmpty()) {
            sliderSpeedModifier.valueFrom = 0f
            sliderSpeedModifier.valueTo = 1f
            sliderSpeedModifier.stepSize = 1f
            sliderSpeedModifier.value = 0f
            sliderSpeedModifier.isEnabled = false
            tvSpeedModifier.text = "Speed Modifier: nicht verfuegbar"
        } else {
            val speedRange = currentSpeedRange()
            val nextValue = sliderSpeedModifier.value
                .toInt()
                .coerceIn(speedRange.first, speedRange.last)
                .toFloat()

            sliderSpeedModifier.valueFrom = minOf(sliderSpeedModifier.value, speedRange.first.toFloat())
            sliderSpeedModifier.valueTo = maxOf(sliderSpeedModifier.value, speedRange.last.toFloat())
            sliderSpeedModifier.stepSize = 1f
            sliderSpeedModifier.value = nextValue
            sliderSpeedModifier.valueFrom = speedRange.first.toFloat()
            sliderSpeedModifier.valueTo = speedRange.last.toFloat()
            sliderSpeedModifier.isEnabled = true
            tvSpeedModifier.text = "Speed Modifier: ${sliderSpeedModifier.value.toInt()} km/h"
        }

        renderExtraCommands()

        val modeLabels = if (selectedModel.maxAdvancedMode > 0) {
            (1..selectedModel.maxAdvancedMode).map { "Mode $it" }
        } else {
            listOf("Keine Advanced-Modes")
        }
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeLabels)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModes.adapter = modeAdapter
        spinnerModes.isEnabled = selectedModel.maxAdvancedMode > 0
        updateCommandAvailability()
    }

    private fun currentSpeedRange(): IntRange {
        val speeds = selectedModel.supportedSpeeds
        val requestedRange = if (cbMoreSpeed.isChecked) {
            MORE_SPEED_MIN..MORE_SPEED_MAX
        } else {
            DEFAULT_SPEED_MIN..DEFAULT_SPEED_MAX
        }
        val first = maxOf(speeds.first(), requestedRange.first)
        val last = minOf(speeds.last(), requestedRange.last)
        return if (first <= last) first..last else speeds.first()..speeds.last()
    }

    private fun renderExtraCommands() {
        extraCommandsContainer.removeAllViews()
        val hasExtraCommands = selectedModel.extraCommands.isNotEmpty()
        tvExtraCommandsLabel.visibility = if (hasExtraCommands) View.VISIBLE else View.GONE
        extraCommandsContainer.visibility = if (hasExtraCommands) View.VISIBLE else View.GONE

        selectedModel.extraCommands.forEach { command ->
            val button = MaterialButton(this).apply {
                text = command.label
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 6.dp }
                setOnClickListener { sendCatalogCommand(command.command, command.label) }
            }
            extraCommandsContainer.addView(button)
        }
    }

    private fun updateCommandAvailability() {
        setCommandButtonState(btnECO, selectedModel.commands.eco)
        setCommandButtonState(btnNormal, selectedModel.commands.normal)
        setCommandButtonState(btnSport, selectedModel.commands.sport)
        setCommandButtonState(btnDev, selectedModel.commands.dev)
        setCommandButtonState(btnLock, selectedModel.commands.lock)
        setCommandButtonState(btnUnlock, selectedModel.commands.unlock)

        val speedCommand = selectedModel.commands.speedCommands.values.firstOrNull()
        val speedReady = selectedModel.supportedSpeeds.isNotEmpty() && isCommandReady(speedCommand)
        sliderSpeedModifier.isEnabled = speedReady
        speedButtons.forEach { (speed, button) ->
            val enabled = selectedModel.commands.speedCommands.containsKey(speed) && speedReady
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun setCommandButtonState(button: MaterialButton, command: ScooterCommandSpec?) {
        val enabled = isCommandReady(command)
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
    }

    private fun isCommandReady(command: ScooterCommandSpec?): Boolean {
        if (command == null) return false
        return !command.requiresDynamicSecret || latestDynamicSecret != null
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
                resetProtocolState()
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
        val inferredModelId = data.getStringExtra(EXTRA_MODEL_ID)
        if (inferredModelId == SO4_FAMILY_MODEL_ID) {
            showSo4VariantDialog(address)
        } else {
            applyInferredModelAndConnect(address, inferredModelId)
        }
    }

    private fun showSo4VariantDialog(address: String) {
        val modelIds = arrayOf("so4", "so4_5_1", "so4_5_2")
        val labels = modelIds
            .map { ScooterCommandCatalog.findModel(it).displayName }
            .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("SO4 erkannt")
            .setItems(labels) { _, which ->
                applyInferredModelAndConnect(address, modelIds[which])
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun applyInferredModelAndConnect(address: String, inferredModelId: String?) {
        val prefsEdit = prefs.edit().putString(KEY_DEVICE_ADDRESS, address)
        if (inferredModelId != null) {
            val inferredModel = ScooterCommandCatalog.findModel(inferredModelId)
            if (inferredModel.id == inferredModelId) {
                selectedModel = inferredModel
                prefsEdit.putString(KEY_MODEL_ID, selectedModel.id)
                resetProtocolState()
                applySelectedModel()
                Toast.makeText(this, "Modell erkannt: ${selectedModel.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
        prefsEdit.apply()
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
        resetProtocolState()
        telemetryFrameBuffer.clear()
        resetTelemetryHeader()
        updateConnectionDropdownLabel("Nicht verbunden")
        if (showToast) Toast.makeText(this, "Getrennt", Toast.LENGTH_SHORT).show()
    }

    private fun resetProtocolState() {
        latestDynamicSecret = null
        sessionToken = null
        realtimeStarted = false
        latestTelemetry = null
        encryptedRxBuffer.clear()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scooterConnected = true
                runOnUiThread { updateConnectionDropdownLabel("Verbunden") }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                scooterConnected = false
                resetProtocolState()
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

            val foundWrite = findProfileWriteCharacteristic(gatt)
            val foundNotify = findProfileNotifyCharacteristic(gatt)

            writeChar = foundWrite
            notifyChar = foundNotify

            runOnUiThread {
                val message = if (writeChar != null && notifyChar != null) {
                    "Profil-UUIDs gefunden"
                } else {
                    "Profil-UUIDs nicht gefunden. Falsches Modell?"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
            if (writeChar != null && notifyChar != null) enableNotifications(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == UUID.fromString(CCCD_UUID)) {
                runOnUiThread { sendStartupCommands() }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val rawData = characteristic.value ?: return
            val plainData = decodeRxBytes(rawData) ?: return
            val rawHex = rawData.joinToString("") { "%02X".format(it) }
            val plainHex = plainData.joinToString("") { "%02X".format(it) }
            Log.d(TAG_BLE, "RX raw: $rawHex")
            Log.d(TAG_BLE, "RX plain: $plainHex")
            ScooterTelemetryParser.extractSessionToken(plainHex)?.let { token ->
                sessionToken = token
            }
            val telemetry = if (scooterConnected) {
                telemetryFrameBuffer.append(plainData, selectedModel.protocolFamily)
            } else {
                null
            }
            telemetry?.dynamicSecret?.let { latestDynamicSecret = it }
            runOnUiThread {
                tvBleOutput.text = plainHex
                if (telemetry != null) {
                    latestTelemetry = mergeTelemetry(latestTelemetry, telemetry)
                    updateTelemetryHeader(latestTelemetry ?: telemetry)
                    updateCommandAvailability()
                }
                if (sessionToken != null && !realtimeStarted) {
                    selectedModel.commands.realtimeStartCommand?.let {
                        realtimeStarted = true
                        sendCatalogCommand(it, "Realtime-Start", quiet = true)
                    }
                }
            }
        }
    }

    private fun updateTelemetryHeader(telemetry: ScooterTelemetry) {
        tvHeaderSpeed.text = telemetry.formattedSpeed
        telemetry.lightOn?.let { lightOn ->
            imgLightOn.visibility = if (lightOn) View.VISIBLE else View.GONE
            imgLightOff.visibility = if (lightOn) View.GONE else View.VISIBLE
        }
        tvHeaderTelemetryDetails.text = formatTelemetryDetails(telemetry)
    }

    private fun mergeTelemetry(old: ScooterTelemetry?, new: ScooterTelemetry): ScooterTelemetry {
        if (old == null) return new

        return new.copy(
            speedKmh = new.speedKmh ?: old.speedKmh,
            lightOn = new.lightOn ?: old.lightOn,
            currentA = new.currentA ?: old.currentA,
            voltageV = new.voltageV ?: old.voltageV,
            batteryLevel = new.batteryLevel ?: old.batteryLevel,
            mileageOfRideKm = new.mileageOfRideKm ?: old.mileageOfRideKm,
            totalMileageKm = new.totalMileageKm ?: old.totalMileageKm,
            remainingMileageKm = new.remainingMileageKm ?: old.remainingMileageKm,
            lockState = new.lockState ?: old.lockState,
            speedMode = new.speedMode ?: old.speedMode,
            fault = new.fault ?: old.fault,
            protocolVersion = new.protocolVersion ?: old.protocolVersion,
            displayVersion = new.displayVersion ?: old.displayVersion,
            cpuVersion = new.cpuVersion ?: old.cpuVersion,
            averageCurrentA = new.averageCurrentA ?: old.averageCurrentA,
            averageSpeedKmh = new.averageSpeedKmh ?: old.averageSpeedKmh,
            chargeCycle = new.chargeCycle ?: old.chargeCycle,
            overflowDischarge = new.overflowDischarge ?: old.overflowDischarge,
            charge = new.charge ?: old.charge,
            energy = new.energy ?: old.energy,
            speedInMiles = new.speedInMiles ?: old.speedInMiles,
            errorCode = new.errorCode ?: old.errorCode,
            timeOfRide = new.timeOfRide ?: old.timeOfRide,
            dynamicSecret = new.dynamicSecret ?: old.dynamicSecret
        )
    }

    private fun resetTelemetryHeader() {
        latestTelemetry = null
        tvHeaderSpeed.text = "00.0 km/h"
        tvHeaderTelemetryDetails.text = "Noch keine Telemetrie"
        imgLightOn.visibility = View.GONE
        imgLightOff.visibility = View.VISIBLE
    }

    private fun formatTelemetryDetails(telemetry: ScooterTelemetry): String {
        val details = mutableListOf<String>()

        telemetry.batteryLevel?.let { details += "Akku ${it.coerceIn(0, 100)}%" }
        telemetry.voltageV?.let { details += "Volt ${it.oneDecimal()} V" }
        telemetry.currentA?.let { details += "Strom ${it.oneDecimal()} A" }
        telemetry.averageCurrentA?.let { details += "Avg ${it.oneDecimal()} A" }
        telemetry.remainingMileageKm?.let { details += "Rest ${it.oneDecimal()} km" }
        telemetry.mileageOfRideKm?.let { details += "Trip ${it.oneDecimal()} km" }
        telemetry.totalMileageKm?.let { details += "Total ${it.noTrailingDecimal()} km" }
        telemetry.timeOfRide?.let { details += "Ride ${it}s" }
        telemetry.energy?.let { details += "Energy ${it.oneDecimal()}" }
        telemetry.averageSpeedKmh?.let { details += "Avg ${it.oneDecimal()} km/h" }
        telemetry.speedMode?.let { details += "Mode $it" }
        telemetry.lockState?.let { details += if (it) "Locked" else "Unlocked" }
        telemetry.charge?.let { details += if (it) "Charging" else "Not charging" }
        telemetry.speedInMiles?.let { if (it) details += "Miles" }
        telemetry.chargeCycle?.let { details += "Cycles $it" }
        telemetry.overflowDischarge?.let { if (it != 0) details += "Overflow $it" }
        telemetry.errorCode?.let {
            if (it != 0) details += "Error 0x${it.toString(16).uppercase().padStart(2, '0')}"
        }
        telemetry.fault?.let {
            if (it != 0) details += "Fault 0x${it.toString(16).uppercase().padStart(2, '0')}"
        }

        return if (details.isEmpty()) "Telemetrie empfangen" else details.joinToString("  |  ")
    }

    private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

    private fun Float.noTrailingDecimal(): String {
        return if (this % 1f == 0f) {
            this.toInt().toString()
        } else {
            oneDecimal()
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        val characteristic = notifyChar ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(UUID.fromString(CCCD_UUID)) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(cccd)
    }

    private fun findProfileWriteCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val profile = selectedModel.bleProfile
        val service = gatt.getService(UUID.fromString(profile.serviceUuid)) ?: return null
        return service.getCharacteristic(UUID.fromString(profile.writeUuid))
    }

    private fun findProfileNotifyCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val profile = selectedModel.bleProfile
        val service = gatt.getService(UUID.fromString(profile.serviceUuid)) ?: return null
        return service.getCharacteristic(UUID.fromString(profile.notifyUuid))
    }

    private fun sendSpeed(speed: Int) {
        val command = selectedModel.speedCommand(speed, currentCommandRuntime())
        if (command == null) {
            Toast.makeText(this, "${selectedModel.displayName} unterstützt $speed km/h nicht", Toast.LENGTH_SHORT).show()
            return
        }
        sendHex(command)
    }

    private fun sendStartupCommands() {
        selectedModel.commands.sessionTokenCommand?.let {
            sendCatalogCommand(it, "Session-Token", quiet = true)
            return
        }

        selectedModel.commands.startupCommand?.let {
            sendCatalogCommand(it, "Startup", quiet = true)
        }
    }

    private fun sendCatalogCommand(
        commandSpec: ScooterCommandSpec?,
        label: String,
        quiet: Boolean = false
    ) {
        if (commandSpec == null) {
            if (!quiet) Toast.makeText(this, "Kein $label-Kommando fuer ${selectedModel.displayName}", Toast.LENGTH_SHORT).show()
            return
        }

        if (commandSpec.requiresSessionToken && sessionToken == null) {
            selectedModel.commands.sessionTokenCommand?.resolve(currentCommandRuntime())?.let { sendHex(it) }
            if (!quiet) {
                Toast.makeText(this, "Session-Token wird angefragt, Kommando danach erneut senden", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val command = commandSpec.resolve(currentCommandRuntime())
        if (command == null) {
            Log.w(
                TAG_BLE,
                "$label konnte nicht aufgeloest werden. model=${selectedModel.id}, secret=$latestDynamicSecret, token=$sessionToken"
            )
            if (!quiet) Toast.makeText(this, "$label konnte nicht aufgeloest werden", Toast.LENGTH_SHORT).show()
            return
        }

        sendHex(command)
    }

    private fun currentCommandRuntime(): CommandRuntimeState {
        return CommandRuntimeState(
            dynamicSecret = latestDynamicSecret,
            sessionToken = sessionToken
        )
    }

    private fun sendHex(hex: String) {
        val writeCharacteristic = writeChar ?: run {
            Toast.makeText(this, "Nicht verbunden oder kein Write-Char", Toast.LENGTH_SHORT).show()
            return
        }

        try {
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
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message ?: "Ungültiges Hex", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Senden fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encodeTxBytes(cleanedHex: String): ByteArray {
        val aesKey = selectedModel.bleProfile.txAesKey
        return if (aesKey == null) {
            HexCodec.toByteArray(cleanedHex)
        } else {
            ScooterTransportCrypto.encryptHexToBytes(cleanedHex, aesKey)
        }
    }

    private fun decodeRxBytes(bytes: ByteArray): ByteArray? {
        val aesKey = selectedModel.bleProfile.rxAesKey ?: return bytes

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

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

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
        private const val KEY_MORE_SPEED = "more_speed_enabled"
        private const val EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        private const val EXTRA_MODEL_ID = "MODEL_ID"
        private const val EXTRA_MODEL_REQUIRED = "MODEL_REQUIRED"
        private const val SO4_FAMILY_MODEL_ID = "so4_family"
        private const val REQ_BLE_PERMS = 2001
        private const val REQ_PICK_DEVICE = 2002
        private const val REQ_PICK_MODEL = 2003
        private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val TAG_BLE = "BLE"
        private const val AES_BLOCK_SIZE = 16
        private const val DEFAULT_SPEED_MIN = 8
        private const val DEFAULT_SPEED_MAX = 30
        private const val MORE_SPEED_MIN = 1
        private const val MORE_SPEED_MAX = 65
    }
}
