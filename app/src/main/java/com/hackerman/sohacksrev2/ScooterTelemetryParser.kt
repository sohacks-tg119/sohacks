package com.hackerman.sohacksrev2

import java.util.Locale

data class ScooterTelemetry(
    val speedKmh: Float? = null,
    val lightOn: Boolean? = null,
    val currentA: Float? = null,
    val voltageV: Float? = null,
    val batteryLevel: Int? = null,
    val mileageOfRideKm: Float? = null,
    val totalMileageKm: Float? = null,
    val remainingMileageKm: Float? = null,
    val lockState: Boolean? = null,
    val speedMode: Int? = null,
    val fault: Int? = null,
    val protocolVersion: String? = null,
    val displayVersion: String? = null,
    val cpuVersion: String? = null,
    val averageCurrentA: Float? = null,
    val averageSpeedKmh: Float? = null,
    val chargeCycle: Int? = null,
    val overflowDischarge: Int? = null,
    val charge: Boolean? = null,
    val energy: Float? = null,
    val speedInMiles: Boolean? = null,
    val errorCode: Int? = null,
    val timeOfRide: Int? = null,
    val dynamicSecret: Int? = null
) {
    val formattedSpeed: String
        get() = String.format(Locale.US, "%04.1f km/h", speedKmh ?: 0f)
}

object ScooterTelemetryParser {
    fun parse(bytes: ByteArray, protocolFamily: ProtocolFamily): ScooterTelemetry? {
        return when (protocolFamily) {
            ProtocolFamily.LEGACY_STATUS -> parseLegacyStatusFrame(bytes)
            ProtocolFamily.SO4_PRO -> parseSo4ProRealtime(bytes)
            ProtocolFamily.D7_SO4_V1 -> parseD7So4Realtime(bytes, v51OrNewer = false)
            ProtocolFamily.D7_SO4_V51_PLUS -> parseD7So4Realtime(bytes, v51OrNewer = true)
            ProtocolFamily.DYNAMIC_D7 -> parseDynamicD7(bytes)
            ProtocolFamily.TWO_BYTE -> parseTwoByte(bytes)
        }
    }

    fun parse(bytes: ByteArray): ScooterTelemetry? {
        return parseTwoByte(bytes)
            ?: parseLegacyStatusFrame(bytes)
            ?: parseD7So4Realtime(bytes, v51OrNewer = true)
            ?: parseDynamicD7(bytes)
    }

    private fun parseSo4ProRealtime(bytes: ByteArray): ScooterTelemetry? {
        return parseLegacyStatusFrame(bytes)
    }

    fun extractSessionToken(hex: String): String? {
        val cleaned = HexCodec.normalize(hex)
        if (!cleaned.startsWith("0601") || cleaned.length < 14) return null
        return cleaned.substring(6, 14)
    }

    private fun parseTwoByte(bytes: ByteArray): ScooterTelemetry? {
        return parseTwoByteRealtime(bytes)
            ?: parseTwoByteCyclingData(bytes)
    }

    private fun parseTwoByteRealtime(bytes: ByteArray): ScooterTelemetry? {
        val hex = bytes.joinToString("") { "%02X".format(it) }
        val start = hex.indexOf("0546")
        if (start < 0 || hex.length < start + 32) return null
        if (hex.substring(start + 4, start + 6) == "01") return null

        return ScooterTelemetry(
            speedKmh = hex.substring(start + 6, start + 10).toInt(16) / 10f,
            currentA = hex.substring(start + 10, start + 14).toInt(16) / 100f,
            voltageV = hex.substring(start + 14, start + 18).toInt(16) / 100f,
            remainingMileageKm = hex.substring(start + 18, start + 22).toInt(16) / 10f,
            batteryLevel = hex.substring(start + 22, start + 24).toInt(16),
            charge = hex.substring(start + 24, start + 26).toInt(16) != 0,
            lockState = hex.substring(start + 26, start + 28) == "00",
            fault = hex.substring(start + 28, start + 32).toInt(16),
            errorCode = hex.substring(start + 28, start + 32).toInt(16)
        )
    }

    private fun parseTwoByteCyclingData(bytes: ByteArray): ScooterTelemetry? {
        val hex = bytes.joinToString("") { "%02X".format(it) }
        val start = hex.indexOf("0548")
        if (start < 0 || hex.length < start + 22) return null

        return ScooterTelemetry(
            timeOfRide = hex.substring(start + 6, start + 10).toInt(16),
            mileageOfRideKm = hex.substring(start + 10, start + 14).toInt(16) / 10f,
            totalMileageKm = hex.substring(start + 14, start + 22).toLong(16) / 100f
        )
    }

    private fun parseD7So4Realtime(bytes: ByteArray, v51OrNewer: Boolean): ScooterTelemetry? {
        val minSize = if (v51OrNewer) MIN_D7_SO4_V51_SIZE else MIN_D7_SO4_V1_SIZE
        val frameStart = findValidD7Frame(bytes, D7_REALTIME_CMD, minSize) ?: return null
        val status = bytes[frameStart + 4].toUnsignedInt()
        val statusBits = status.bits8()
        val mileageIndex = if (v51OrNewer) frameStart + 15 else frameStart + 14
        val totalIndex = if (v51OrNewer) frameStart + 17 else frameStart + 16
        val batteryIndex = if (v51OrNewer) frameStart + 19 else frameStart + 18

        if (bytes.size <= batteryIndex) return null

        return ScooterTelemetry(
            speedKmh = bytes.u16(frameStart + 5) / 10f,
            currentA = bytes.u16(frameStart + 9) / 10f,
            voltageV = bytes.u16(frameStart + 7) / 10f,
            batteryLevel = bytes[batteryIndex].toUnsignedInt(),
            mileageOfRideKm = bytes.u16(mileageIndex) / 10f,
            totalMileageKm = bytes.u16(totalIndex).toFloat(),
            lockState = statusBits[0] == '1',
            speedMode = statusBits.substring(4, 7).toInt(2),
            protocolVersion = bytes.getOrNull(frameStart + 12)?.versionString(),
            displayVersion = if (v51OrNewer) bytes.getOrNull(frameStart + 13)?.versionString() else null,
            cpuVersion = bytes.getOrNull(if (v51OrNewer) frameStart + 14 else frameStart + 13)?.versionString()
        )
    }

    private fun parseDynamicD7(bytes: ByteArray): ScooterTelemetry? {
        return parseDynamicD7Realtime(bytes)
            ?: parseDynamicD7Standby(bytes)
    }

    private fun parseDynamicD7Realtime(bytes: ByteArray): ScooterTelemetry? {
        findLooseD7RealtimeFrame(bytes)?.let { frameStart ->
            return parseDynamicRealtimePayload(bytes, frameStart, frameStart + 4)
        }

        findRawDynamicRealtimeFrame(bytes)?.let { frameStart ->
            return parseDynamicRealtimePayload(bytes, frameStart, frameStart + 4)
        }

        return null
    }

    private fun parseDynamicRealtimePayload(
        bytes: ByteArray,
        frameStart: Int,
        statusIndex: Int
    ): ScooterTelemetry? {
        if (bytes.size <= statusIndex + 15) return null

        val status = bytes[statusIndex].toUnsignedInt()
        val statusBits = status.bits8()

        return ScooterTelemetry(
            speedKmh = bytes.u16(statusIndex + 1) / 10f,
            currentA = bytes.u16(statusIndex + 5) / 10f,
            voltageV = bytes.u16(statusIndex + 3) / 10f,
            speedMode = statusBits.substring(4, 7).toInt(2),
            energy = bytes.u16(statusIndex + 9) / 10f,
            speedInMiles = statusBits[3] == '1',
            dynamicSecret = calculateDynamicSecret(bytes, frameStart)
        )
    }

    private fun parseDynamicD7Standby(bytes: ByteArray): ScooterTelemetry? {
        val frameStart = findLooseD7CommandFrame(bytes, D7_STANDBY_CMD, MIN_DYNAMIC_STANDBY_SIZE)
            ?: findRawDynamicCommandFrame(bytes, D7_STANDBY_CMD, MIN_DYNAMIC_STANDBY_SIZE)
            ?: return null

        if (bytes.size <= frameStart + 16) return null

        val faultValue = bytes[frameStart + 3].toUnsignedInt()
        val battery = bytes[frameStart + 11].toUnsignedInt()

        return ScooterTelemetry(
            fault = faultValue,
            lockState = parseAntiTheftFromFault(faultValue),
            protocolVersion = bytes[frameStart + 4].versionString(),
            displayVersion = bytes[frameStart + 5].versionString(),
            cpuVersion = bytes[frameStart + 5].versionString(),
            mileageOfRideKm = bytes.u16(frameStart + 6) / 10f,
            totalMileageKm = bytes.u16(frameStart + 8).toFloat(),
            averageCurrentA = bytes[frameStart + 10].toUnsignedInt() / 10f,
            batteryLevel = if (battery >= 90) 100 else battery,
            remainingMileageKm = bytes[frameStart + 12].toUnsignedInt() / 10f,
            averageSpeedKmh = bytes.u16(frameStart + 13) / 10f,
            chargeCycle = bytes[frameStart + 15].toUnsignedInt(),
            overflowDischarge = bytes[frameStart + 16].toUnsignedInt()
        )
    }

    private fun parseAntiTheftFromFault(fault: Int): Boolean {
        return fault and 0x01 != 0
    }

    private fun parseLegacyStatusFrame(bytes: ByteArray): ScooterTelemetry? {
        val start = findStatusFrameStart(bytes) ?: return null
        val status = bytes[start].toUnsignedInt()

        return ScooterTelemetry(
            speedKmh = bytes[start + 0x02].toUnsignedInt() / 10f,
            lightOn = status and 0x01 == 1
        )
    }

    private fun findValidD7Frame(bytes: ByteArray, commandId: Int, minSize: Int): Int? {
        if (bytes.size < minSize) return null

        for (index in 0..bytes.size - minSize) {
            if (bytes[index].toUnsignedInt() != 0xD7) continue
            val length = bytes[index + 1].toUnsignedInt()
            if (length < minSize || index + length > bytes.size) continue
            if (bytes[index + 2].toUnsignedInt() != commandId) continue
            if (hasValidD7Checksum(bytes, index, length)) return index
        }
        return null
    }

    private fun findLooseD7RealtimeFrame(bytes: ByteArray): Int? {
        if (bytes.size < MIN_D7_SO4_V51_SIZE) return null

        for (index in 0..bytes.size - MIN_D7_SO4_V51_SIZE) {
            if (
                bytes[index].toUnsignedInt() == 0xD7 &&
                bytes[index + 2].toUnsignedInt() == D7_REALTIME_CMD
            ) {
                return index
            }
        }

        return null
    }

    private fun findRawDynamicRealtimeFrame(bytes: ByteArray): Int? {
        if (bytes.size < MIN_RAW_REALTIME_SIZE) return null

        for (index in 0..bytes.size - MIN_RAW_REALTIME_SIZE) {
            if (bytes[index + 2].toUnsignedInt() == D7_REALTIME_CMD) return index
        }

        return null
    }

    private fun findLooseD7CommandFrame(bytes: ByteArray, commandId: Int, minSize: Int): Int? {
        if (bytes.size < minSize) return null

        for (index in 0..bytes.size - minSize) {
            if (
                bytes[index].toUnsignedInt() == 0xD7 &&
                bytes[index + 2].toUnsignedInt() == commandId
            ) {
                return index
            }
        }

        return null
    }

    private fun findRawDynamicCommandFrame(bytes: ByteArray, commandId: Int, minSize: Int): Int? {
        if (bytes.size < minSize) return null

        for (index in 0..bytes.size - minSize) {
            if (bytes[index + 2].toUnsignedInt() == commandId) return index
        }

        return null
    }

    private fun hasValidD7Checksum(bytes: ByteArray, start: Int, length: Int): Boolean {
        val checksumIndex = start + length - 1
        val checksum = (start + 1 until checksumIndex).sumOf { bytes[it].toUnsignedInt() } and 0xFF
        return checksum == bytes[checksumIndex].toUnsignedInt()
    }

    private fun calculateDynamicSecret(bytes: ByteArray, frameStart: Int): Int? {
        if (bytes.size <= frameStart + 16) return null

        val byte3 = bytes[frameStart + 3].toUnsignedInt()
        val byte15 = bytes[frameStart + 15].toUnsignedInt()
        val byte16 = bytes[frameStart + 16].toUnsignedInt()
        val mixed = (byte16 xor byte3) xor (byte15 xor byte3)

        return ((((((((mixed + 0xCE) xor 0xB2) + 0xA5) xor 0xCA) + (byte3 and 0x0F)) xor 0x2B) + 0x33) xor 0x1D) and 0x7F
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

    private fun ByteArray.u16(index: Int): Int {
        return (this[index].toUnsignedInt() shl 8) or this[index + 1].toUnsignedInt()
    }

    private fun Byte.versionString(): String {
        val value = toUnsignedInt()
        return "${(value shr 4) and 0x0F}.${value and 0x0F}"
    }

    private fun Int.bits8(): String = toString(2).padStart(8, '0').takeLast(8)

    private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

    private const val MIN_STATUS_FRAME_SIZE = 0x11
    private const val D7_REALTIME_CMD = 0x1D
    private const val D7_STANDBY_CMD = 0x2D
    private const val MIN_D7_SO4_V1_SIZE = 20
    private const val MIN_D7_SO4_V51_SIZE = 20
    private const val MIN_RAW_REALTIME_SIZE = 18
    private const val MIN_DYNAMIC_STANDBY_SIZE = 17
}

class ScooterTelemetryFrameBuffer {
    private val buffer = mutableListOf<Byte>()

    fun append(bytes: ByteArray, protocolFamily: ProtocolFamily): ScooterTelemetry? {
        buffer += bytes.toList()
        val telemetry = ScooterTelemetryParser.parse(buffer.toByteArray(), protocolFamily)

        if (telemetry != null) {
            buffer.clear()
        } else if (buffer.size > MAX_BUFFER_SIZE) {
            val keep = buffer.takeLast(MIN_REASSEMBLY_BYTES)
            buffer.clear()
            buffer += keep
        }

        return telemetry
    }

    fun append(bytes: ByteArray): ScooterTelemetry? = append(bytes, ProtocolFamily.LEGACY_STATUS)

    fun clear() {
        buffer.clear()
    }

    private companion object {
        private const val MAX_BUFFER_SIZE = 160
        private const val MIN_REASSEMBLY_BYTES = 64
    }
}
