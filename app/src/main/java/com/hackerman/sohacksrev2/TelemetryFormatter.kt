package com.hackerman.sohacksrev2

import java.util.Locale

/**
 * Reine Anzeige-Formatierung fuer Telemetrie.
 *
 * WICHTIG: Hier findet keine Interpretation / kein Parsing der Rohdaten statt –
 * das bleibt vollstaendig in [ScooterTelemetryParser]. Diese Klasse wandelt nur
 * bereits ausgelesene Werte in Anzeige-Strings um.
 */
object TelemetryFormatter {

    const val PLACEHOLDER = "—"

    fun battery(t: ScooterTelemetry): String =
        t.batteryLevel?.let { "${it.coerceIn(0, 100)} %" } ?: PLACEHOLDER

    fun voltage(t: ScooterTelemetry): String =
        t.voltageV?.let { "${it.oneDecimal()} V" } ?: PLACEHOLDER

    fun current(t: ScooterTelemetry): String =
        t.currentA?.let { "${it.oneDecimal()} A" } ?: PLACEHOLDER

    fun range(t: ScooterTelemetry): String =
        t.remainingMileageKm?.let { "${it.oneDecimal()} km" } ?: PLACEHOLDER

    fun trip(t: ScooterTelemetry): String =
        t.mileageOfRideKm?.let { "${it.oneDecimal()} km" } ?: PLACEHOLDER

    fun mode(t: ScooterTelemetry): String =
        t.speedMode?.toString() ?: PLACEHOLDER

    /**
     * Sekundaerzeile fuer alle Werte, die nicht als Kachel dargestellt werden.
     * Entspricht in Umfang der frueheren Detail-Zeile, ohne die sechs
     * Kachel-Werte (Akku, Spannung, Strom, Restweite, Trip, Modus) zu doppeln.
     */
    fun secondaryDetails(t: ScooterTelemetry): String {
        val details = mutableListOf<String>()

        t.totalMileageKm?.let { details += "Total ${it.noTrailingDecimal()} km" }
        t.averageSpeedKmh?.let { details += "Ø ${it.oneDecimal()} km/h" }
        t.averageCurrentA?.let { details += "Ø ${it.oneDecimal()} A" }
        t.timeOfRide?.let { details += "Fahrt ${it}s" }
        t.energy?.let { details += "Energie ${it.oneDecimal()}" }
        t.lockState?.let { details += if (it) "Gesperrt" else "Entsperrt" }
        t.charge?.let { details += if (it) "Laedt" else "Laedt nicht" }
        t.speedInMiles?.let { if (it) details += "Meilen" }
        t.chargeCycle?.let { details += "Zyklen $it" }
        t.overflowDischarge?.let { if (it != 0) details += "Overflow $it" }
        t.errorCode?.let {
            if (it != 0) details += "Fehler 0x${it.toString(16).uppercase().padStart(2, '0')}"
        }
        t.fault?.let {
            if (it != 0) details += "Fault 0x${it.toString(16).uppercase().padStart(2, '0')}"
        }

        return if (details.isEmpty()) "Telemetrie empfangen" else details.joinToString("   •   ")
    }

    private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

    private fun Float.noTrailingDecimal(): String {
        return if (this % 1f == 0f) this.toInt().toString() else oneDecimal()
    }
}
