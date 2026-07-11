package com.hackerman.sohacksrev2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

/**
 * Duenne UI-Schicht.
 *
 * Verantwortlich fuer: View-Binding, Beobachten des [MainViewModel] und
 * Weiterleiten von Benutzeraktionen, sowie Android-Lebenszyklus-Themen
 * (Disclaimer, Berechtigungen, Modell-/Geraeteauswahl). Es findet hier KEINE
 * BLE- oder Protokoll-Logik mehr statt – die liegt vollstaendig im ViewModel
 * bzw. im [ScooterBleManager].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences

    // --- Header ---
    private lateinit var btnOpenModelList: MaterialButton
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSubtitle: TextView
    private lateinit var tvConnectionStatus: TextView

    // --- Telemetrie-Dashboard ---
    private lateinit var tvHeaderSpeed: TextView
    private lateinit var tvHeaderTelemetryDetails: TextView
    private lateinit var imgLightOff: ImageView
    private lateinit var imgLightOn: ImageView
    private lateinit var tvBatteryValue: TextView
    private lateinit var tvVoltageValue: TextView
    private lateinit var tvCurrentValue: TextView
    private lateinit var tvRangeValue: TextView
    private lateinit var tvTripValue: TextView
    private lateinit var tvModeValue: TextView
    private lateinit var progressSpeed: ProgressBar
    private lateinit var progressBattery: ProgressBar

    // --- Fahrmodus / Sperre ---
    private lateinit var btnECO: MaterialButton
    private lateinit var btnNormal: MaterialButton
    private lateinit var btnSport: MaterialButton
    private lateinit var btnDev: MaterialButton
    private lateinit var btnLock: MaterialButton
    private lateinit var btnUnlock: MaterialButton

    // --- Geschwindigkeit ---
    private lateinit var sliderSpeedModifier: Slider
    private lateinit var tvSpeedModifier: TextView
    private lateinit var speedButtons: Map<Int, MaterialButton>

    // --- Verbindung ---
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnChangeDevice: MaterialButton
    private lateinit var btnDisconnect: MaterialButton

    // --- Erweitert ---
    private lateinit var cardAdvanced: View
    private lateinit var switchAdvanced: Switch
    private lateinit var advancedContainer: View
    private lateinit var spinnerModes: Spinner
    private lateinit var cbMoreSpeed: CheckBox
    private lateinit var tvExtraCommandsLabel: TextView
    private lateinit var extraCommandsContainer: LinearLayout
    private lateinit var txtCmdHex: EditText
    private lateinit var btnSendHex: MaterialButton
    private lateinit var tvBleOutput: TextView

    // --- Neue GUI ---
    private lateinit var mainScroll: View
    private lateinit var easyModeContainer: View
    private lateinit var easyMap: MapView
    private lateinit var easySheetScroll: NestedScrollView
    private lateinit var easyBottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var tvEasyConnectionStatus: TextView
    private lateinit var tvNewGuiPowerBadge: TextView
    private lateinit var tvEasyLocationHint: TextView
    private lateinit var tvEasyVoltage: TextView
    private lateinit var tvEasySpeed: TextView
    private lateinit var tvEasyMode: TextView
    private lateinit var tvEasyBatteryValue: TextView
    private lateinit var tvEasyVoltageDetail: TextView
    private lateinit var tvEasyCurrentValue: TextView
    private lateinit var tvEasyRangeValue: TextView
    private lateinit var tvEasyTripValue: TextView
    private lateinit var tvEasyModeValue: TextView
    private lateinit var tvEasyTelemetryDetails: TextView
    private lateinit var progressEasyBattery: ProgressBar
    private lateinit var tvEasySpeedModifier: TextView
    private lateinit var sliderEasySpeedModifier: Slider
    private lateinit var btnEasyECO: MaterialButton
    private lateinit var btnEasyNormal: MaterialButton
    private lateinit var btnEasySport: MaterialButton
    private lateinit var btnEasyDev: MaterialButton
    private lateinit var easySpeedButtons: Map<Int, MaterialButton>
    private lateinit var btnEasyLock: MaterialButton
    private lateinit var btnEasyUnlock: MaterialButton
    private lateinit var btnEasyConnect: MaterialButton
    private lateinit var btnEasyChangeDevice: MaterialButton
    private lateinit var btnEasyDisconnect: MaterialButton
    private lateinit var cardEasyAdvanced: View
    private lateinit var switchEasyAdvanced: Switch
    private lateinit var easyAdvancedContainer: View
    private lateinit var easyAdvancedModeSpinner: Spinner
    private lateinit var cbEasyMoreSpeed: CheckBox
    private lateinit var tvEasyExtraCommandsLabel: TextView
    private lateinit var easyExtraCommandsContainer: LinearLayout
    private lateinit var txtEasyCmdHex: EditText
    private lateinit var btnEasySendHex: MaterialButton
    private lateinit var tvEasyBleOutput: TextView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var newGuiVisible = false
    private var locationPermissionRequested = false

    private var startupCompleted = false
    private var autoReconnectAttempted = false
    private var initialDeviceScanInProgress = false
    private var pendingInitialDeviceAddress: String? = null
    private var connectionState = ConnectionState.DISCONNECTED
    private var latestAvailability = CommandAvailability()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialDeviceScanInProgress = savedInstanceState?.getBoolean(STATE_INITIAL_SCAN_IN_PROGRESS) ?: false
        pendingInitialDeviceAddress = savedInstanceState?.getString(STATE_PENDING_DEVICE_ADDRESS)

        bindUi()
        setupCommandButtons()
        setupConnectionButtons()
        setupSpeedControls()
        setupAdvancedControls()
        setupEasyMode()
        observeViewModel()

        resetTelemetryHeader()

        // Initialen Zustand aus den Preferences ins ViewModel spiegeln.
        viewModel.setModel(ScooterCommandCatalog.findModel(prefs.getString(KEY_MODEL_ID, null)))
        viewModel.setMoreSpeed(cbMoreSpeed.isChecked)
        applyAdvancedPreference()

        runInitialSetup()
        applyNewGuiPreference(requestLocation = startupCompleted)
    }

    override fun onResume() {
        super.onResume()
        AppPreferences.applyKeepScreenOn(this)
        if (::prefs.isInitialized) {
            applyAdvancedPreference()
            applyNewGuiPreference(requestLocation = true)
        }
    }

    override fun onPause() {
        if (::easyMap.isInitialized) easyMap.onPause()
        myLocationOverlay?.disableMyLocation()
        super.onPause()
    }

    override fun onDestroy() {
        myLocationOverlay?.disableMyLocation()
        if (::easyMap.isInitialized) easyMap.onDetach()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Binding
    // ---------------------------------------------------------------------

    private fun bindUi() {
        mainScroll = findViewById(R.id.mainScroll)
        btnOpenModelList = findViewById(R.id.btnOpenModelList)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        tvHeaderSpeed = findViewById(R.id.tvHeaderSpeed)
        tvHeaderTelemetryDetails = findViewById(R.id.tvHeaderTelemetryDetails)
        imgLightOff = findViewById(R.id.imgLightOff)
        imgLightOn = findViewById(R.id.imgLightOn)
        tvBatteryValue = findViewById(R.id.tvBatteryValue)
        tvVoltageValue = findViewById(R.id.tvVoltageValue)
        tvCurrentValue = findViewById(R.id.tvCurrentValue)
        tvRangeValue = findViewById(R.id.tvRangeValue)
        tvTripValue = findViewById(R.id.tvTripValue)
        tvModeValue = findViewById(R.id.tvModeValue)
        progressSpeed = findViewById(R.id.progressSpeed)
        progressBattery = findViewById(R.id.progressBattery)

        btnECO = findViewById(R.id.btnECO)
        btnNormal = findViewById(R.id.btnNormal)
        btnSport = findViewById(R.id.btnSport)
        btnDev = findViewById(R.id.btnDev)
        btnLock = findViewById(R.id.btnLock)
        btnUnlock = findViewById(R.id.btnUnlock)

        sliderSpeedModifier = findViewById(R.id.sliderSpeedModifier)
        tvSpeedModifier = findViewById(R.id.tvSpeedModifier)
        speedButtons = mapOf(
            8 to findViewById(R.id.btn8kmh),
            15 to findViewById(R.id.btn15kmh),
            20 to findViewById(R.id.btn20kmh),
            25 to findViewById(R.id.btn25kmh),
            30 to findViewById(R.id.btn30kmh)
        )

        btnConnect = findViewById(R.id.btnConnect)
        btnChangeDevice = findViewById(R.id.btnChangeDevice)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        cardAdvanced = findViewById(R.id.cardAdvanced)
        switchAdvanced = findViewById(R.id.switchAdvanced)
        advancedContainer = findViewById(R.id.advancedContainer)
        spinnerModes = findViewById(R.id.advanced_dropdown_1_to_254)
        cbMoreSpeed = findViewById(R.id.cbMoreSpeed)
        tvExtraCommandsLabel = findViewById(R.id.tvExtraCommandsLabel)
        extraCommandsContainer = findViewById(R.id.extraCommandsContainer)
        txtCmdHex = findViewById(R.id.txt_cmd_hex)
        btnSendHex = findViewById(R.id.btnSendHex)
        tvBleOutput = findViewById(R.id.tvBleOutput)

        easyModeContainer = findViewById(R.id.easyModeContainer)
        easyMap = findViewById(R.id.easyMap)
        easySheetScroll = findViewById(R.id.easySheetScroll)
        tvEasyConnectionStatus = findViewById(R.id.tvEasyConnectionStatus)
        tvNewGuiPowerBadge = findViewById(R.id.tvNewGuiPowerBadge)
        tvEasyLocationHint = findViewById(R.id.tvEasyLocationHint)
        tvEasyVoltage = findViewById(R.id.tvEasyVoltage)
        tvEasySpeed = findViewById(R.id.tvEasySpeed)
        tvEasyMode = findViewById(R.id.tvEasyMode)
        tvEasyBatteryValue = findViewById(R.id.tvEasyBatteryValue)
        tvEasyVoltageDetail = findViewById(R.id.tvEasyVoltageDetail)
        tvEasyCurrentValue = findViewById(R.id.tvEasyCurrentValue)
        tvEasyRangeValue = findViewById(R.id.tvEasyRangeValue)
        tvEasyTripValue = findViewById(R.id.tvEasyTripValue)
        tvEasyModeValue = findViewById(R.id.tvEasyModeValue)
        tvEasyTelemetryDetails = findViewById(R.id.tvEasyTelemetryDetails)
        progressEasyBattery = findViewById(R.id.progressEasyBattery)
        tvEasySpeedModifier = findViewById(R.id.tvEasySpeedModifier)
        sliderEasySpeedModifier = findViewById(R.id.sliderEasySpeedModifier)
        btnEasyECO = findViewById(R.id.btnEasyECO)
        btnEasyNormal = findViewById(R.id.btnEasyNormal)
        btnEasySport = findViewById(R.id.btnEasySport)
        btnEasyDev = findViewById(R.id.btnEasyDev)
        easySpeedButtons = mapOf(
            8 to findViewById(R.id.btnEasy8kmh),
            15 to findViewById(R.id.btnEasy15kmh),
            20 to findViewById(R.id.btnEasy20kmh),
            25 to findViewById(R.id.btnEasy25kmh),
            30 to findViewById(R.id.btnEasy30kmh)
        )
        btnEasyLock = findViewById(R.id.btnEasyLock)
        btnEasyUnlock = findViewById(R.id.btnEasyUnlock)
        btnEasyConnect = findViewById(R.id.btnEasyConnect)
        btnEasyChangeDevice = findViewById(R.id.btnEasyChangeDevice)
        btnEasyDisconnect = findViewById(R.id.btnEasyDisconnect)
        cardEasyAdvanced = findViewById(R.id.cardEasyAdvanced)
        switchEasyAdvanced = findViewById(R.id.switchEasyAdvanced)
        easyAdvancedContainer = findViewById(R.id.easyAdvancedContainer)
        easyAdvancedModeSpinner = findViewById(R.id.easyAdvancedModeSpinner)
        cbEasyMoreSpeed = findViewById(R.id.cbEasyMoreSpeed)
        tvEasyExtraCommandsLabel = findViewById(R.id.tvEasyExtraCommandsLabel)
        easyExtraCommandsContainer = findViewById(R.id.easyExtraCommandsContainer)
        txtEasyCmdHex = findViewById(R.id.txtEasyCmdHex)
        btnEasySendHex = findViewById(R.id.btnEasySendHex)
        tvEasyBleOutput = findViewById(R.id.tvEasyBleOutput)
    }

    // ---------------------------------------------------------------------
    // Beobachtung des ViewModels
    // ---------------------------------------------------------------------

    private fun observeViewModel() {
        viewModel.connectionState.observe(this) { updateConnectionUi(it) }
        viewModel.model.observe(this) { onModelChanged(it) }
        viewModel.availability.observe(this) { applyAvailability(it) }
        viewModel.telemetry.observe(this) { telemetry ->
            if (telemetry == null) resetTelemetryHeader() else updateDashboard(telemetry)
        }
        viewModel.bleOutput.observe(this) { hex ->
            tvBleOutput.text = hex ?: "Noch keine Daten"
            tvEasyBleOutput.text = hex ?: "Noch keine Daten"
        }
        viewModel.toast.observe(this) { event ->
            event.getIfNotHandled()?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun updateConnectionUi(state: ConnectionState) {
        connectionState = state
        tvConnectionStatus.text = "● ${state.label}"
        tvEasyConnectionStatus.text = "● ${state.label}"
        val colorRes = when (state) {
            ConnectionState.CONNECTED -> R.color.colorSecondary
            ConnectionState.CONNECTING -> R.color.colorWarning
            ConnectionState.DISCONNECTED -> R.color.textColorSecondary
        }
        tvConnectionStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        tvEasyConnectionStatus.setTextColor(ContextCompat.getColor(this, colorRes))

        val busy = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
        btnConnect.isEnabled = !busy
        btnDisconnect.isEnabled = busy
        btnEasyConnect.isEnabled = !busy
        btnEasyDisconnect.isEnabled = busy
    }

    private fun onModelChanged(model: ScooterModel) {
        tvAppTitle.text = model.displayName
        tvAppSubtitle.text = "BLE-Steuerung"
        buildModeSpinner(model.maxAdvancedMode)
        buildExtraCommands(model.extraCommands)
    }

    private fun applyAvailability(av: CommandAvailability) {
        latestAvailability = av
        setModeButtonState(btnECO, av.ecoEnabled)
        setModeButtonState(btnNormal, av.normalEnabled)
        setModeButtonState(btnSport, av.sportEnabled)
        setModeButtonState(btnDev, av.devEnabled)
        setModeButtonState(btnLock, av.lockEnabled)
        setModeButtonState(btnUnlock, av.unlockEnabled)
        setModeButtonState(btnEasyECO, av.ecoEnabled)
        setModeButtonState(btnEasyNormal, av.normalEnabled)
        setModeButtonState(btnEasySport, av.sportEnabled)
        setModeButtonState(btnEasyDev, av.devEnabled)
        setModeButtonState(btnEasyLock, av.lockEnabled)
        setModeButtonState(btnEasyUnlock, av.unlockEnabled)

        applySpeedSlider(av)
        applyEasySpeedSlider(av)

        speedButtons.forEach { (speed, button) ->
            val enabled = av.enabledSpeedButtons.contains(speed)
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.45f
        }
        easySpeedButtons.forEach { (speed, button) ->
            val enabled = av.enabledSpeedButtons.contains(speed)
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun setModeButtonState(button: MaterialButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
    }

    private fun applySpeedSlider(av: CommandAvailability) {
        if (!av.hasSpeeds) {
            sliderSpeedModifier.valueFrom = 0f
            sliderSpeedModifier.valueTo = 1f
            sliderSpeedModifier.stepSize = 1f
            sliderSpeedModifier.value = 0f
            sliderSpeedModifier.isEnabled = false
            tvSpeedModifier.text = "nicht verfügbar"
            return
        }

        val range = av.speedRange
        val nextValue = sliderSpeedModifier.value
            .toInt()
            .coerceIn(range.first, range.last)
            .toFloat()

        // Reihenfolge wichtig, damit der Material-Slider nie einen Wert
        // ausserhalb des aktuellen Bereichs haelt (sonst IllegalStateException).
        sliderSpeedModifier.valueFrom = minOf(sliderSpeedModifier.value, range.first.toFloat())
        sliderSpeedModifier.valueTo = maxOf(sliderSpeedModifier.value, range.last.toFloat())
        sliderSpeedModifier.stepSize = 1f
        sliderSpeedModifier.value = nextValue
        sliderSpeedModifier.valueFrom = range.first.toFloat()
        sliderSpeedModifier.valueTo = range.last.toFloat()
        sliderSpeedModifier.isEnabled = av.speedEnabled
        tvSpeedModifier.text = "${sliderSpeedModifier.value.toInt()} km/h"
    }

    private fun applyEasySpeedSlider(av: CommandAvailability) {
        if (!av.hasSpeeds) {
            sliderEasySpeedModifier.valueFrom = 0f
            sliderEasySpeedModifier.valueTo = 1f
            sliderEasySpeedModifier.stepSize = 1f
            sliderEasySpeedModifier.value = 0f
            sliderEasySpeedModifier.isEnabled = false
            tvEasySpeedModifier.text = "nicht verfügbar"
            return
        }

        val range = av.speedRange
        val nextValue = sliderEasySpeedModifier.value
            .toInt()
            .coerceIn(range.first, range.last)
            .toFloat()
        sliderEasySpeedModifier.valueFrom = minOf(sliderEasySpeedModifier.value, range.first.toFloat())
        sliderEasySpeedModifier.valueTo = maxOf(sliderEasySpeedModifier.value, range.last.toFloat())
        sliderEasySpeedModifier.stepSize = 1f
        sliderEasySpeedModifier.value = nextValue
        sliderEasySpeedModifier.valueFrom = range.first.toFloat()
        sliderEasySpeedModifier.valueTo = range.last.toFloat()
        sliderEasySpeedModifier.isEnabled = av.speedEnabled
        tvEasySpeedModifier.text = "${nextValue.toInt()} km/h"
    }

    private fun updateDashboard(telemetry: ScooterTelemetry) {
        tvHeaderSpeed.text = telemetry.formattedSpeed
        telemetry.lightOn?.let { lightOn ->
            imgLightOn.visibility = if (lightOn) View.VISIBLE else View.GONE
            imgLightOff.visibility = if (lightOn) View.GONE else View.VISIBLE
        }
        tvBatteryValue.text = TelemetryFormatter.battery(telemetry)
        tvVoltageValue.text = TelemetryFormatter.voltage(telemetry)
        tvCurrentValue.text = TelemetryFormatter.current(telemetry)
        tvRangeValue.text = TelemetryFormatter.range(telemetry)
        tvTripValue.text = TelemetryFormatter.trip(telemetry)
        tvModeValue.text = TelemetryFormatter.mode(telemetry)
        tvHeaderTelemetryDetails.text = TelemetryFormatter.secondaryDetails(telemetry)
        tvEasySpeed.text = telemetry.speedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "0.0"
        tvEasyVoltage.text = TelemetryFormatter.voltage(telemetry)
        tvEasyMode.text = telemetry.speedMode?.let { "Mode $it" } ?: TelemetryFormatter.PLACEHOLDER
        tvEasyBatteryValue.text = TelemetryFormatter.battery(telemetry)
        tvEasyVoltageDetail.text = TelemetryFormatter.voltage(telemetry)
        tvEasyCurrentValue.text = TelemetryFormatter.current(telemetry)
        tvEasyRangeValue.text = TelemetryFormatter.range(telemetry)
        tvEasyTripValue.text = TelemetryFormatter.trip(telemetry)
        tvEasyModeValue.text = TelemetryFormatter.mode(telemetry)
        tvEasyTelemetryDetails.text = buildString {
            append(TelemetryFormatter.secondaryDetails(telemetry))
            telemetry.lightOn?.let { append(if (it) "   •   Licht an" else "   •   Licht aus") }
        }
        tvNewGuiPowerBadge.text = if (telemetry.voltageV != null && telemetry.currentA != null) {
            String.format(Locale.US, "%.1f W", telemetry.voltageV * telemetry.currentA)
        } else {
            "— W"
        }

        val speedPercent = telemetry.speedKmh?.let {
            ((it / SPEED_GAUGE_MAX_KMH) * 100f).toInt().coerceIn(0, 100)
        } ?: 0
        progressSpeed.progress = speedPercent
        progressBattery.progress = telemetry.batteryLevel?.coerceIn(0, 100) ?: 0
        progressEasyBattery.progress = telemetry.batteryLevel?.coerceIn(0, 100) ?: 0
    }

    private fun resetTelemetryHeader() {
        tvHeaderSpeed.text = "00.0 km/h"
        tvHeaderTelemetryDetails.text = "Noch keine Telemetrie"
        tvBatteryValue.text = TelemetryFormatter.PLACEHOLDER
        tvVoltageValue.text = TelemetryFormatter.PLACEHOLDER
        tvCurrentValue.text = TelemetryFormatter.PLACEHOLDER
        tvRangeValue.text = TelemetryFormatter.PLACEHOLDER
        tvTripValue.text = TelemetryFormatter.PLACEHOLDER
        tvModeValue.text = TelemetryFormatter.PLACEHOLDER
        tvEasySpeed.text = "0.0"
        tvEasyVoltage.text = TelemetryFormatter.PLACEHOLDER
        tvEasyMode.text = TelemetryFormatter.PLACEHOLDER
        tvEasyBatteryValue.text = TelemetryFormatter.PLACEHOLDER
        tvEasyVoltageDetail.text = TelemetryFormatter.PLACEHOLDER
        tvEasyCurrentValue.text = TelemetryFormatter.PLACEHOLDER
        tvEasyRangeValue.text = TelemetryFormatter.PLACEHOLDER
        tvEasyTripValue.text = TelemetryFormatter.PLACEHOLDER
        tvEasyModeValue.text = TelemetryFormatter.PLACEHOLDER
        tvEasyTelemetryDetails.text = "Noch keine Telemetrie"
        tvNewGuiPowerBadge.text = "— W"
        progressEasyBattery.progress = 0
        progressSpeed.progress = 0
        progressBattery.progress = 0
        imgLightOn.visibility = View.GONE
        imgLightOff.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------------
    // Aktionen / Listener
    // ---------------------------------------------------------------------

    private fun setupCommandButtons() {
        btnOpenModelList.setOnClickListener { openModelSelection(required = false) }
        btnECO.setOnClickListener { viewModel.sendEco() }
        btnNormal.setOnClickListener { viewModel.sendNormal() }
        btnSport.setOnClickListener { viewModel.sendSport() }
        btnDev.setOnClickListener { viewModel.sendDev() }
        btnLock.setOnClickListener { viewModel.sendLock() }
        btnUnlock.setOnClickListener { viewModel.sendUnlock() }
        speedButtons.forEach { (speed, button) ->
            button.setOnClickListener { viewModel.sendSpeed(speed) }
        }
    }

    private fun setupConnectionButtons() {
        btnConnect.setOnClickListener {
            if (connectionState == ConnectionState.DISCONNECTED) {
                connectLastDeviceOrPick()
            } else {
                Toast.makeText(this, "Bereits verbunden", Toast.LENGTH_SHORT).show()
            }
        }
        btnChangeDevice.setOnClickListener {
            doDisconnect(showToast = false)
            pickDevice()
        }
        btnDisconnect.setOnClickListener { doDisconnect(showToast = true) }
    }

    private fun setupSpeedControls() {
        sliderSpeedModifier.addOnChangeListener { _, value, fromUser ->
            if (!latestAvailability.hasSpeeds) return@addOnChangeListener
            val range = latestAvailability.speedRange
            val speed = value.toInt().coerceIn(range.first, range.last)
            tvSpeedModifier.text = "$speed km/h"
            if (fromUser) viewModel.sendSpeed(speed)
        }
    }

    private fun setupAdvancedControls() {
        cbMoreSpeed.isChecked = prefs.getBoolean(KEY_MORE_SPEED, false)
        cbMoreSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MORE_SPEED, isChecked).apply()
            viewModel.setMoreSpeed(isChecked)
            if (::cbEasyMoreSpeed.isInitialized && cbEasyMoreSpeed.isChecked != isChecked) {
                cbEasyMoreSpeed.isChecked = isChecked
            }
        }

        switchAdvanced.setOnCheckedChangeListener { _, isChecked ->
            advancedContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            advancedContainer.requestLayout()
        }

        spinnerModes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!switchAdvanced.isChecked) return
                viewModel.sendAdvancedMode(position + 1)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnSendHex.setOnClickListener {
            val hex = txtCmdHex.text.toString().trim()
            if (hex.isEmpty()) {
                Toast.makeText(this, "Bitte Hex eingeben", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.sendCustomHex(hex)
            }
        }
    }

    private fun setupEasyMode() {
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
        }
        easyMap.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(51.1657, 10.4515))
        }

        easyBottomSheetBehavior = BottomSheetBehavior.from(findViewById<View>(R.id.easyFooter)).apply {
            peekHeight = 132.dp
            isHideable = false
            isFitToContents = false
            expandedOffset = 72.dp
            isDraggable = true
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        (cardEasyAdvanced.parent as? LinearLayout)?.let { parent ->
            parent.removeView(cardEasyAdvanced)
            parent.addView(cardEasyAdvanced, 2)
        }

        findViewById<MaterialButton>(R.id.btnEasyBack).setOnClickListener {
            openModelSelection(required = false)
        }

        findViewById<MaterialButton>(R.id.btnEasySettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        tvEasyConnectionStatus.setOnClickListener {
            if (connectionState == ConnectionState.DISCONNECTED) connectLastDeviceOrPick()
        }
        tvEasyLocationHint.setOnClickListener { ensureLocationPermission(forceRequest = true) }

        btnEasyECO.setOnClickListener { viewModel.sendEco() }
        btnEasyNormal.setOnClickListener { viewModel.sendNormal() }
        btnEasySport.setOnClickListener { viewModel.sendSport() }
        btnEasyDev.setOnClickListener { viewModel.sendDev() }
        btnEasyLock.setOnClickListener { viewModel.sendLock() }
        btnEasyUnlock.setOnClickListener { viewModel.sendUnlock() }
        easySpeedButtons.forEach { (speed, button) ->
            button.setOnClickListener { viewModel.sendSpeed(speed) }
        }
        sliderEasySpeedModifier.addOnChangeListener { _, value, fromUser ->
            if (!latestAvailability.hasSpeeds) return@addOnChangeListener
            val range = latestAvailability.speedRange
            val speed = value.toInt().coerceIn(range.first, range.last)
            tvEasySpeedModifier.text = "$speed km/h"
            if (fromUser) viewModel.sendSpeed(speed)
        }
        btnEasyConnect.setOnClickListener {
            if (connectionState == ConnectionState.DISCONNECTED) connectLastDeviceOrPick()
        }
        btnEasyChangeDevice.setOnClickListener {
            doDisconnect(showToast = false)
            pickDevice()
        }
        btnEasyDisconnect.setOnClickListener { doDisconnect(showToast = true) }

        cbEasyMoreSpeed.isChecked = prefs.getBoolean(KEY_MORE_SPEED, false)
        cbEasyMoreSpeed.setOnCheckedChangeListener { _, isChecked ->
            if (cbMoreSpeed.isChecked != isChecked) {
                cbMoreSpeed.isChecked = isChecked
            }
        }
        switchEasyAdvanced.setOnCheckedChangeListener { _, isChecked ->
            easyAdvancedContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            easyAdvancedContainer.requestLayout()
            if (isChecked) {
                easyBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                easySheetScroll.post {
                    easySheetScroll.smoothScrollTo(0, (cardEasyAdvanced.top - 8.dp).coerceAtLeast(0))
                }
            }
        }
        easyAdvancedModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (switchEasyAdvanced.isChecked) viewModel.sendAdvancedMode(position + 1)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        btnEasySendHex.setOnClickListener {
            val hex = txtEasyCmdHex.text.toString().trim()
            if (hex.isEmpty()) {
                Toast.makeText(this, "Bitte Hex eingeben", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.sendCustomHex(hex)
            }
        }
    }

    private fun buildModeSpinner(maxAdvancedMode: Int) {
        val labels = if (maxAdvancedMode > 0) {
            (1..maxAdvancedMode).map { "Mode $it" }
        } else {
            listOf("Keine Advanced-Modes")
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModes.adapter = adapter
        spinnerModes.isEnabled = maxAdvancedMode > 0
        easyAdvancedModeSpinner.adapter = adapter
        easyAdvancedModeSpinner.isEnabled = maxAdvancedMode > 0
    }

    private fun buildExtraCommands(commands: List<NamedScooterCommand>) {
        renderExtraCommands(extraCommandsContainer, tvExtraCommandsLabel, commands)
        renderExtraCommands(easyExtraCommandsContainer, tvEasyExtraCommandsLabel, commands)
    }

    private fun renderExtraCommands(
        container: LinearLayout,
        label: TextView,
        commands: List<NamedScooterCommand>
    ) {
        container.removeAllViews()
        val hasCommands = commands.isNotEmpty()
        label.visibility = if (hasCommands) View.VISIBLE else View.GONE
        container.visibility = if (hasCommands) View.VISIBLE else View.GONE

        commands.forEach { command ->
            container.addView(MaterialButton(this).apply {
                text = command.label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorOnSecondary))
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.colorSecondary)
                )
                cornerRadius = 12.dp
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 6.dp }
                setOnClickListener { viewModel.sendExtra(command) }
            })
        }
    }

    private fun applyAdvancedPreference() {
        val enabled = prefs.getBoolean(KEY_ADVANCED_OPTIONS, false)
        cardAdvanced.visibility = if (enabled) View.VISIBLE else View.GONE
        cardEasyAdvanced.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            switchAdvanced.isChecked = false
            advancedContainer.visibility = View.GONE
            switchEasyAdvanced.isChecked = false
            easyAdvancedContainer.visibility = View.GONE
        }
    }

    private fun applyNewGuiPreference(requestLocation: Boolean) {
        val hasScooter = prefs.getString(KEY_DEVICE_ADDRESS, null) != null &&
            prefs.getString(KEY_MODEL_ID, null) != null
        newGuiVisible = prefs.getBoolean(AppPreferences.KEY_NEW_GUI, true) && hasScooter

        mainScroll.visibility = if (newGuiVisible) View.GONE else View.VISIBLE
        easyModeContainer.visibility = if (newGuiVisible) View.VISIBLE else View.GONE
        tvNewGuiPowerBadge.visibility = if (
            newGuiVisible && prefs.getBoolean(AppPreferences.KEY_WATT_BADGE, false)
        ) View.VISIBLE else View.GONE

        if (newGuiVisible) {
            easyMap.onResume()
            if (requestLocation) ensureLocationPermission()
        } else {
            myLocationOverlay?.disableMyLocation()
        }
    }

    private fun ensureLocationPermission(forceRequest: Boolean = false) {
        if (!newGuiVisible) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startLocationOverlay()
            return
        }

        tvEasyLocationHint.visibility = View.VISIBLE
        tvEasyLocationHint.text = "Standortfreigabe erforderlich"
        if (locationPermissionRequested && !forceRequest) return
        locationPermissionRequested = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQ_LOCATION_PERMS
        )
    }

    private fun startLocationOverlay() {
        if (!newGuiVisible) return

        val overlay = myLocationOverlay ?: MyLocationNewOverlay(
            GpsMyLocationProvider(applicationContext),
            easyMap
        ).also {
            val driverArrow = drawableToBitmap(R.drawable.ic_driver_arrow_blue, 48)
            it.setPersonIcon(driverArrow)
            it.setDirectionIcon(driverArrow)
            it.setPersonAnchor(0.5f, 0.5f)
            it.setDirectionAnchor(0.5f, 0.5f)
            myLocationOverlay = it
            easyMap.overlays.add(it)
        }

        tvEasyLocationHint.visibility = View.VISIBLE
        tvEasyLocationHint.text = "Standort wird gesucht …"
        overlay.enableMyLocation()
        overlay.enableFollowLocation()
        overlay.runOnFirstFix {
            runOnUiThread {
                val location = overlay.myLocation ?: return@runOnUiThread
                easyMap.controller.animateTo(location)
                easyMap.controller.setZoom(17.5)
                tvEasyLocationHint.visibility = View.GONE
            }
        }
    }

    private fun drawableToBitmap(drawableRes: Int, sizeDp: Int): Bitmap {
        val sizePx = sizeDp.dp
        val drawable = requireNotNull(AppCompatResources.getDrawable(this, drawableRes))
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
        }
    }

    // ---------------------------------------------------------------------
    // Verbindungs-Flows
    // ---------------------------------------------------------------------

    private fun connectLastDeviceOrPick() {
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null)
        if (address == null) {
            pickDevice()
        } else {
            viewModel.connect(address)
        }
    }

    private fun autoReconnectLastDevice() {
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null) ?: return
        viewModel.connect(address)
    }

    private fun doDisconnect(showToast: Boolean) {
        viewModel.disconnect()
        if (showToast) Toast.makeText(this, "Getrennt", Toast.LENGTH_SHORT).show()
    }

    private fun pickDevice() {
        startActivityForResult(Intent(this, DeviceSelectionActivity1::class.java), REQ_PICK_DEVICE)
    }

    private fun openModelSelection(required: Boolean) {
        val intent = Intent(this, ModelSelectionActivity::class.java)
        intent.putExtra(EXTRA_MODEL_REQUIRED, required)
        startActivityForResult(intent, REQ_PICK_MODEL)
    }

    // ---------------------------------------------------------------------
    // Erst-Setup: Disclaimer, Berechtigungen, Scan, Modell, Auto-Reconnect
    // ---------------------------------------------------------------------

    private fun runInitialSetup() {
        if (!prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) {
            showDisclaimer()
            return
        }

        if (!hasBlePermissions()) {
            requestBlePermissions()
            return
        }

        if (prefs.getString(KEY_MODEL_ID, null) == null) {
            // Beim allerersten Start wird zuerst ein Scooter gesucht. Erst
            // nachdem das Geraet gewaehlt wurde, folgt die Modellauswahl.
            if (!initialDeviceScanInProgress && pendingInitialDeviceAddress == null) {
                initialDeviceScanInProgress = true
                pickDevice()
            }
            return
        }

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
        ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
        if (requestCode == REQ_LOCATION_PERMS) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionRequested = false
                startLocationOverlay()
            } else {
                tvEasyLocationHint.visibility = View.VISIBLE
                tvEasyLocationHint.text = "Ohne Standortfreigabe kann deine Position nicht angezeigt werden"
            }
            return
        }
        if (requestCode != REQ_BLE_PERMS) return

        if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            runInitialSetup()
        } else {
            Toast.makeText(this, "Berechtigungen fehlen für BLE-Verbindung", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_PICK_MODEL) {
            val modelId = data?.getStringExtra(EXTRA_MODEL_ID) ?: prefs.getString(KEY_MODEL_ID, null)
            if (resultCode == Activity.RESULT_OK && modelId != null) {
                val model = ScooterCommandCatalog.findModel(modelId)
                prefs.edit().putString(KEY_MODEL_ID, model.id).apply()
                viewModel.setModel(model)
                applyAdvancedPreference()
                val initialAddress = pendingInitialDeviceAddress
                if (!startupCompleted && initialAddress != null) {
                    pendingInitialDeviceAddress = null
                    startupCompleted = true
                    autoReconnectAttempted = true
                    prefs.edit().putString(KEY_DEVICE_ADDRESS, initialAddress).apply()
                    applyNewGuiPreference(requestLocation = true)
                    viewModel.connect(initialAddress)
                } else if (!startupCompleted) {
                    runInitialSetup()
                }
            } else if (startupCompleted) {
                applyAdvancedPreference()
            } else {
                pendingInitialDeviceAddress = null
                finish()
            }
            return
        }

        if (requestCode != REQ_PICK_DEVICE) return
        initialDeviceScanInProgress = false
        if (resultCode != Activity.RESULT_OK) return

        val address = data?.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return
        val inferredModelId = data.getStringExtra(EXTRA_MODEL_ID)
        if (!startupCompleted && prefs.getString(KEY_MODEL_ID, null) == null) {
            pendingInitialDeviceAddress = address
            openModelSelection(required = true)
            return
        }

        if (inferredModelId == SO4_FAMILY_MODEL_ID) {
            showSo4VariantDialog(address)
        } else {
            applyInferredModelAndConnect(address, inferredModelId)
        }
    }

    private fun showSo4VariantDialog(address: String) {
        val modelIds = arrayOf("s04_pro_gen2", "s04_pro_gen3", "so4", "so4_5_1", "so4_5_2")
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_INITIAL_SCAN_IN_PROGRESS, initialDeviceScanInProgress)
        outState.putString(STATE_PENDING_DEVICE_ADDRESS, pendingInitialDeviceAddress)
        super.onSaveInstanceState(outState)
    }

    private fun applyInferredModelAndConnect(address: String, inferredModelId: String?) {
        val prefsEdit = prefs.edit().putString(KEY_DEVICE_ADDRESS, address)
        if (inferredModelId != null) {
            val inferredModel = ScooterCommandCatalog.findModel(inferredModelId)
            if (inferredModel.id == inferredModelId) {
                prefsEdit.putString(KEY_MODEL_ID, inferredModel.id)
                viewModel.setModel(inferredModel)
                Toast.makeText(this, "Modell erkannt: ${inferredModel.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
        prefsEdit.apply()
        applyNewGuiPreference(requestLocation = true)
        viewModel.connect(address)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
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
        private const val STATE_INITIAL_SCAN_IN_PROGRESS = "initial_scan_in_progress"
        private const val STATE_PENDING_DEVICE_ADDRESS = "pending_initial_device_address"
        private const val REQ_BLE_PERMS = 2001
        private const val REQ_PICK_DEVICE = 2002
        private const val REQ_PICK_MODEL = 2003
        private const val REQ_LOCATION_PERMS = 2004

        /** Anzeige-Maximum der Speed-Gauge (reine Skala fuer die Balkenanzeige). */
        private const val SPEED_GAUGE_MAX_KMH = 40f
    }
}
