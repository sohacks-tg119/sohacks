package com.hackerman.sohacksrev2

import java.util.Locale

data class ScooterTelemetry(
    val speedKmh: Float,
    val lightOn: Boolean
) {
    val formattedSpeed: String
        get() = String.format(Locale.US, "%04.1f km/h", speedKmh)
}

object ScooterTelemetryParser {
    private const val LIGHT_STATUS_OFFSET = 0x00
    private const val SPEED_OFFSET = 0x02
    private const val MIN_STATUS_FRAME_SIZE = 0x11

    fun parse(bytes: ByteArray): ScooterTelemetry? {
        val start = findStatusFrameStart(bytes) ?: return null
        val status = bytes[start + LIGHT_STATUS_OFFSET].toUnsignedInt()
        val speedKmh = bytes[start + SPEED_OFFSET].toUnsignedInt() / 10f

        return ScooterTelemetry(
            speedKmh = speedKmh,
            lightOn = status and 0x01 == 1
        )
    }

    private fun findStatusFrameStart(bytes: ByteArray): Int? {
        if (bytes.size < MIN_STATUS_FRAME_SIZE) return null

        for (index in 0..bytes.size - MIN_STATUS_FRAME_SIZE) {
            if (looksLikeStatusFrame(bytes, index)) return index
        }
        return null
    }

    private fun looksLikeStatusFrame(bytes: ByteArray, start: Int): Boolean {
        val status = bytes[start].toUnsignedInt()
        return status in 0x40..0x4F &&
            bytes[start + 0x03].toUnsignedInt() == 0x02 &&
            bytes[start + 0x07].toUnsignedInt() == 0x40 &&
            bytes[start + 0x08].toUnsignedInt() == 0x54 &&
            bytes[start + 0x09].toUnsignedInt() == 0x44 &&
            bytes[start + 0x0A].toUnsignedInt() == 0x43
    }

    private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF
}

class ScooterTelemetryFrameBuffer {
    private val buffer = mutableListOf<Byte>()

    fun append(bytes: ByteArray): ScooterTelemetry? {
        buffer += bytes.toList()
        val telemetry = ScooterTelemetryParser.parse(buffer.toByteArray())

        if (telemetry != null) {
            buffer.clear()
        } else if (buffer.size > MAX_BUFFER_SIZE) {
            val keep = buffer.takeLast(MIN_REASSEMBLY_BYTES)
            buffer.clear()
            buffer += keep
        }

        return telemetry
    }

    fun clear() {
        buffer.clear()
    }

    private companion object {
        private const val MAX_BUFFER_SIZE = 96
        private const val MIN_REASSEMBLY_BYTES = 40
    }
}
