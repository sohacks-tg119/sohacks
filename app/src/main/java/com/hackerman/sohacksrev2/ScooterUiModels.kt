package com.hackerman.sohacksrev2

/**
 * Kleine UI-/Zustands-Hilfstypen fuer die neue Architektur.
 *
 * Diese Datei enthaelt bewusst keine Protokoll-Logik – sie beschreibt nur
 * Zustaende, die zwischen [ScooterBleManager], [MainViewModel] und der UI
 * ausgetauscht werden.
 */

/** Verbindungszustand der BLE-Verbindung inklusive Anzeige-Label. */
enum class ConnectionState(val label: String) {
    DISCONNECTED("Nicht verbunden"),
    CONNECTING("Verbinde…"),
    CONNECTED("Verbunden")
}

/** Ergebnis eines Sendevorgangs, damit die UI passende Rueckmeldung geben kann. */
sealed class SendResult {
    object Sent : SendResult()
    object NotConnected : SendResult()
    data class InvalidHex(val message: String?) : SendResult()
    data class Error(val message: String?) : SendResult()
}

/**
 * Momentane Verfuegbarkeit der Kommandos.
 *
 * Wird bei jedem Modellwechsel, jeder Telemetrie-Aktualisierung (Dynamic-Secret)
 * und bei Aenderung der "More Speed"-Option neu berechnet. Die UI aktiviert /
 * deaktiviert daraufhin nur ihre Buttons – ohne dabei Strukturen (Spinner,
 * Extra-Buttons) neu aufzubauen.
 */
data class CommandAvailability(
    val ecoEnabled: Boolean = false,
    val normalEnabled: Boolean = false,
    val sportEnabled: Boolean = false,
    val devEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
    val unlockEnabled: Boolean = false,
    val speedEnabled: Boolean = false,
    val hasSpeeds: Boolean = false,
    val enabledSpeedButtons: Set<Int> = emptySet(),
    val speedRange: IntRange = 0..0
)

/**
 * Einweg-Wrapper fuer LiveData-Events (Toasts, Dialoge), damit sie nach einer
 * Konfigurationsaenderung nicht erneut ausgeloest werden.
 */
open class Event<out T>(private val content: T) {
    private var handled = false

    fun getIfNotHandled(): T? {
        return if (handled) {
            null
        } else {
            handled = true
            content
        }
    }
}
