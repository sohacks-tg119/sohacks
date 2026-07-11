package com.hackerman.sohacksrev2

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Haelt den gesamten Laufzeit-/Protokoll-Zustand und ueberlebt
 * Konfigurationsaenderungen (z.B. Drehen des Geraets bleibt verbunden).
 *
 * Die Sende- und Lese-Logik (Session-Token, Dynamic-Secret, Telemetrie-Merge,
 * Kommando-Aufloesung, Startup-/Realtime-Sequenz) ist 1:1 aus der frueheren
 * MainActivity uebernommen – es wird exakt das Gleiche gesendet und ausgelesen.
 * Die UI erhaelt den Zustand ausschliesslich ueber LiveData.
 */
class MainViewModel(application: Application) : AndroidViewModel(application), ScooterBleManager.Listener {

    private val ble = ScooterBleManager(application.applicationContext).also { it.listener = this }

    private var selectedModel: ScooterModel = ScooterCommandCatalog.defaultModel
    private var moreSpeed: Boolean = false

    // --- Protokoll-Zustand (identisch zur frueheren Activity) ---
    private var latestDynamicSecret: Int? = null
    private var sessionToken: String? = null
    private var realtimeStarted = false
    private var latestTelemetry: ScooterTelemetry? = null
    private val telemetryFrameBuffer = ScooterTelemetryFrameBuffer()

    // --- Beobachtbarer UI-Zustand ---
    private val _connectionState = MutableLiveData(ConnectionState.DISCONNECTED)
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _model = MutableLiveData(selectedModel)
    val model: LiveData<ScooterModel> = _model

    private val _telemetry = MutableLiveData<ScooterTelemetry?>(null)
    val telemetry: LiveData<ScooterTelemetry?> = _telemetry

    private val _bleOutput = MutableLiveData<String?>(null)
    val bleOutput: LiveData<String?> = _bleOutput

    private val _availability = MutableLiveData(CommandAvailability())
    val availability: LiveData<CommandAvailability> = _availability

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    private val isConnected: Boolean get() = _connectionState.value == ConnectionState.CONNECTED

    init {
        ble.model = selectedModel
        publishAvailability()
    }

    // ---------------------------------------------------------------------
    // Konfiguration durch die UI
    // ---------------------------------------------------------------------

    fun setModel(model: ScooterModel) {
        val modelChanged = selectedModel.id != model.id
        selectedModel = model
        ble.model = model
        if (modelChanged) {
            resetProtocolState()
            telemetryFrameBuffer.clear()
            _telemetry.value = null
            _bleOutput.value = null
        }
        _model.value = model
        publishAvailability()
    }

    fun setMoreSpeed(enabled: Boolean) {
        moreSpeed = enabled
        publishAvailability()
    }

    fun currentModel(): ScooterModel = selectedModel

    // ---------------------------------------------------------------------
    // Verbindung
    // ---------------------------------------------------------------------

    fun connect(address: String) {
        ble.connect(address)
    }

    fun disconnect() {
        ble.disconnect()
        resetProtocolState()
        telemetryFrameBuffer.clear()
        latestTelemetry = null
        _telemetry.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        publishAvailability()
    }

    private fun resetProtocolState() {
        latestDynamicSecret = null
        sessionToken = null
        realtimeStarted = false
        latestTelemetry = null
    }

    // ---------------------------------------------------------------------
    // Kommandos (oeffentliche Aktionen fuer die UI)
    // ---------------------------------------------------------------------

    fun sendEco() = sendCatalogCommand(selectedModel.commands.eco, "ECO")
    fun sendNormal() = sendCatalogCommand(selectedModel.commands.normal, "Normal")
    fun sendSport() = sendCatalogCommand(selectedModel.commands.sport, "Sport")
    fun sendDev() = sendCatalogCommand(selectedModel.commands.dev, "Dev")
    fun sendLock() = sendCatalogCommand(selectedModel.commands.lock, "Lock")
    fun sendUnlock() = sendCatalogCommand(selectedModel.commands.unlock, "Unlock")
    fun sendExtra(command: NamedScooterCommand) = sendCatalogCommand(command.command, command.label)

    fun sendSpeed(speed: Int) {
        val command = selectedModel.speedCommand(speed, currentCommandRuntime())
        if (command == null) {
            toast("${selectedModel.displayName} unterstützt $speed km/h nicht")
            return
        }
        sendHex(command)
    }

    fun sendAdvancedMode(mode: Int) {
        val command = selectedModel.advancedModeCommand(mode)
        if (command == null) {
            toast("Kein Kommando für Mode $mode")
            return
        }
        sendHex(command)
        toast("Mode $mode gesendet")
    }

    fun sendCustomHex(hex: String) {
        sendHex(hex)
    }

    // ---------------------------------------------------------------------
    // ScooterBleManager.Listener
    // ---------------------------------------------------------------------

    override fun onConnectionStateChanged(state: ConnectionState) {
        _connectionState.value = state
        if (state == ConnectionState.DISCONNECTED) {
            resetProtocolState()
            telemetryFrameBuffer.clear()
            latestTelemetry = null
            _telemetry.value = null
            publishAvailability()
        }
    }

    override fun onProfileResolved(writeFound: Boolean, notifyFound: Boolean) {
        toast(
            if (writeFound && notifyFound) "Profil-UUIDs gefunden"
            else "Profil-UUIDs nicht gefunden. Falsches Modell?"
        )
    }

    override fun onReadyForStartup() {
        sendStartupCommands()
    }

    override fun onPlainDataReceived(plainBytes: ByteArray, plainHex: String) {
        ScooterTelemetryParser.extractSessionToken(plainHex)?.let { token ->
            sessionToken = token
        }
        val telemetry = if (isConnected) {
            telemetryFrameBuffer.append(plainBytes, selectedModel.protocolFamily)
        } else {
            null
        }
        telemetry?.dynamicSecret?.let { latestDynamicSecret = it }

        _bleOutput.value = plainHex
        if (telemetry != null) {
            latestTelemetry = mergeTelemetry(latestTelemetry, telemetry)
            _telemetry.value = latestTelemetry
            publishAvailability()
        }
        if (sessionToken != null && !realtimeStarted) {
            selectedModel.commands.realtimeStartCommand?.let {
                realtimeStarted = true
                sendCatalogCommand(it, "Realtime-Start", quiet = true)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Kommando-Aufloesung (identisch zur frueheren Activity)
    // ---------------------------------------------------------------------

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
            if (!quiet) toast("Kein $label-Kommando fuer ${selectedModel.displayName}")
            return
        }

        if (commandSpec.requiresSessionToken && sessionToken == null) {
            selectedModel.commands.sessionTokenCommand?.resolve(currentCommandRuntime())?.let { sendHex(it) }
            if (!quiet) {
                toast("Session-Token wird angefragt, Kommando danach erneut senden")
            }
            return
        }

        val command = commandSpec.resolve(currentCommandRuntime())
        if (command == null) {
            Log.w(
                TAG_BLE,
                "$label konnte nicht aufgeloest werden. model=${selectedModel.id}, secret=$latestDynamicSecret, token=$sessionToken"
            )
            if (!quiet) toast("$label konnte nicht aufgeloest werden")
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
        when (val result = ble.sendRawHex(hex)) {
            is SendResult.Sent -> Unit
            is SendResult.NotConnected -> toast("Nicht verbunden oder kein Write-Char")
            is SendResult.InvalidHex -> toast(result.message ?: "Ungültiges Hex")
            is SendResult.Error -> toast("Senden fehlgeschlagen: ${result.message}")
        }
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

    // ---------------------------------------------------------------------
    // Verfuegbarkeit / Slider-Bereich (identische Logik)
    // ---------------------------------------------------------------------

    private fun publishAvailability() {
        val commands = selectedModel.commands
        val speedCommand = commands.speedCommands.values.firstOrNull()
        val hasSpeeds = selectedModel.supportedSpeeds.isNotEmpty()
        val speedReady = hasSpeeds && isCommandReady(speedCommand)

        val enabledSpeedButtons = SPEED_BUTTONS
            .filter { commands.speedCommands.containsKey(it) && speedReady }
            .toSet()

        _availability.value = CommandAvailability(
            ecoEnabled = isCommandReady(commands.eco),
            normalEnabled = isCommandReady(commands.normal),
            sportEnabled = isCommandReady(commands.sport),
            devEnabled = isCommandReady(commands.dev),
            lockEnabled = isCommandReady(commands.lock),
            unlockEnabled = isCommandReady(commands.unlock),
            speedEnabled = speedReady,
            hasSpeeds = hasSpeeds,
            enabledSpeedButtons = enabledSpeedButtons,
            speedRange = if (hasSpeeds) currentSpeedRange() else 0..0
        )
    }

    private fun isCommandReady(command: ScooterCommandSpec?): Boolean {
        if (command == null) return false
        return !command.requiresDynamicSecret || latestDynamicSecret != null
    }

    private fun currentSpeedRange(): IntRange {
        val speeds = selectedModel.supportedSpeeds
        val requestedRange = if (moreSpeed) {
            MORE_SPEED_MIN..MORE_SPEED_MAX
        } else {
            DEFAULT_SPEED_MIN..DEFAULT_SPEED_MAX
        }
        val first = maxOf(speeds.first(), requestedRange.first)
        val last = minOf(speeds.last(), requestedRange.last)
        return if (first <= last) first..last else speeds.first()..speeds.last()
    }

    private fun toast(message: String) {
        _toast.value = Event(message)
    }

    override fun onCleared() {
        super.onCleared()
        ble.disconnect()
    }

    companion object {
        private const val TAG_BLE = "BLE"
        private val SPEED_BUTTONS = listOf(8, 15, 20, 25, 30)
        private const val DEFAULT_SPEED_MIN = 8
        private const val DEFAULT_SPEED_MAX = 30
        private const val MORE_SPEED_MIN = 1
        private const val MORE_SPEED_MAX = 65
    }
}
