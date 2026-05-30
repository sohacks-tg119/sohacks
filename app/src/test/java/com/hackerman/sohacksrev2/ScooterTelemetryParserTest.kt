package com.hackerman.sohacksrev2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScooterTelemetryParserTest {
    @Test
    fun parse_readsLightOnAndSpeedFromStatusFrame() {
        val frame = HexCodec.toByteArray(
            "43 00 15 02 16 00 00 40 54 44 43 00 00 05 AB 64 DC 01 14 01 01 00 80 1E"
        )

        val telemetry = ScooterTelemetryParser.parse(frame)

        assertEquals(2.1f, telemetry!!.speedKmh!!)
        assertEquals("02.1 km/h", telemetry.formattedSpeed)
        assertTrue(telemetry.lightOn == true)
    }

    @Test
    fun parse_readsLightOffAndZeroSpeed() {
        val frame = HexCodec.toByteArray(
            "42 00 00 02 16 00 00 40 54 44 43 00 00 05 AB 64 C5 01 14 01 01 00 80 1E"
        )

        val telemetry = ScooterTelemetryParser.parse(frame)

        assertEquals(0.0f, telemetry!!.speedKmh!!)
        assertEquals("00.0 km/h", telemetry.formattedSpeed)
        assertFalse(telemetry.lightOn == true)
    }

    @Test
    fun parse_ignoresNonStatusFrames() {
        val tailFrame = HexCodec.toByteArray("01 14 01 01 00 80 1E 00 5F 00 00 1E 15 0F 01 8B")

        assertNull(ScooterTelemetryParser.parse(tailFrame))
    }

    @Test
    fun parse_readsLegacyOddEvenLightMarkers() {
        val lightOn = HexCodec.toByteArray(
            "45 00 10 02 16 00 00 40 54 44 43 00 00 05 AB 64 D6 01"
        )
        val lightOff = HexCodec.toByteArray(
            "44 00 10 02 16 00 00 40 54 44 43 00 00 05 AB 64 D5 01"
        )

        assertTrue(ScooterTelemetryParser.parse(lightOn)!!.lightOn == true)
        assertFalse(ScooterTelemetryParser.parse(lightOff)!!.lightOn == true)
    }

    @Test
    fun parse_readsD7RealtimeFrames() {
        val frame = HexCodec.toByteArray(
            "D7 15 1D 00 04 00 FA 01 E0 04 D2 00 24 25 26 00 7B 01 23 64 59"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.D7_SO4_V51_PLUS)

        assertEquals(25.0f, telemetry!!.speedKmh!!)
        assertEquals(48.0f, telemetry.voltageV)
        assertEquals(123.4f, telemetry.currentA)
        assertEquals(100, telemetry.batteryLevel)
        assertEquals(2, telemetry.speedMode)
        assertFalse(telemetry.lockState!!)
        assertEquals(12.3f, telemetry.mileageOfRideKm)
        assertEquals(291.0f, telemetry.totalMileageKm)
        assertEquals("2.4", telemetry.protocolVersion)
        assertEquals("2.5", telemetry.displayVersion)
        assertEquals("2.6", telemetry.cpuVersion)
    }

    @Test
    fun parse_readsD7So4V1RealtimeFramesWithLegacyIndices() {
        val frame = HexCodec.toByteArray(
            "D7 14 1D 00 04 00 FA 01 E0 04 D2 00 24 25 00 7B 01 23 64 32"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.D7_SO4_V1)

        assertEquals(25.0f, telemetry!!.speedKmh!!)
        assertEquals(48.0f, telemetry.voltageV)
        assertEquals(123.4f, telemetry.currentA)
        assertEquals(100, telemetry.batteryLevel)
        assertEquals(12.3f, telemetry.mileageOfRideKm)
        assertEquals(291.0f, telemetry.totalMileageKm)
        assertEquals("2.4", telemetry.protocolVersion)
        assertNull(telemetry.displayVersion)
        assertEquals("2.5", telemetry.cpuVersion)
    }

    @Test
    fun parse_so4ProAcceptsMiniLegacyStatusFrames() {
        val frame = HexCodec.toByteArray(
            "43 00 15 02 16 00 00 40 54 44 43 00 00 05 AB 64 DC 01 14 01 01 00 80 1E"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.SO4_PRO)

        assertEquals(2.1f, telemetry!!.speedKmh!!)
        assertTrue(telemetry.lightOn == true)
    }

    @Test
    fun parse_so4ProDoesNotGuessGen2TelemetryFrameFields() {
        val frame = HexCodec.toByteArray(
            "01 14 01 01 00 80 1E 00 5F 00 00 1E 15 0F 01 8B 01 F4 0C A3 D5 15 1D 0A"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.SO4_PRO)

        assertNull(telemetry)
    }

    @Test
    fun parse_so4ProIgnoresUnknownGen2FrameAfterTdcStatus() {
        val frame = HexCodec.toByteArray(
            "43 00 15 02 16 00 00 40 54 44 43 00 00 05 AB 64 DC " +
                "01 14 01 01 00 80 1E 00 5F 00 00 1E 15 0F 01 8B 01 F4 0C A3 D5 15 1D 0A"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.SO4_PRO)

        assertEquals(2.1f, telemetry!!.speedKmh!!)
        assertTrue(telemetry.lightOn == true)
        assertNull(telemetry.batteryLevel)
        assertNull(telemetry.voltageV)
        assertNull(telemetry.currentA)
    }

    @Test
    fun parse_dynamicD7AcceptsLooseFrameWithoutValidChecksum() {
        val frame = HexCodec.toByteArray(
            "D7 15 1D 00 04 00 FA 01 E0 04 D2 00 24 25 26 00 7B 01 23 64 00"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.DYNAMIC_D7)

        assertEquals(25.0f, telemetry!!.speedKmh!!)
        assertEquals(48.0f, telemetry.voltageV)
        assertEquals(123.4f, telemetry.currentA)
        assertEquals(2, telemetry.speedMode)
    }

    @Test
    fun parse_dynamicD7AcceptsRawRealtimeFrame() {
        val frame = HexCodec.toByteArray(
            "00 00 1D 00 04 00 FA 01 E0 04 D2 00 24 25 26 00 7B 01 23 64"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.DYNAMIC_D7)

        assertEquals(25.0f, telemetry!!.speedKmh!!)
        assertEquals(48.0f, telemetry.voltageV)
        assertEquals(123.4f, telemetry.currentA)
        assertEquals(2, telemetry.speedMode)
    }

    @Test
    fun parse_dynamicD7ReadsStandbyFrames() {
        val frame = HexCodec.toByteArray(
            "D7 12 2D 81 24 25 00 7B 01 23 2A 64 96 00 FA 05 02 38"
        )

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.DYNAMIC_D7)

        assertNull(telemetry!!.speedKmh)
        assertEquals(0x81, telemetry.fault)
        assertEquals("2.4", telemetry.protocolVersion)
        assertEquals("2.5", telemetry.displayVersion)
        assertEquals("2.5", telemetry.cpuVersion)
        assertTrue(telemetry.lockState == true)
        assertEquals(12.3f, telemetry.mileageOfRideKm)
        assertEquals(291.0f, telemetry.totalMileageKm)
        assertEquals(4.2f, telemetry.averageCurrentA)
        assertEquals(100, telemetry.batteryLevel)
        assertEquals(15.0f, telemetry.remainingMileageKm)
        assertEquals(25.0f, telemetry.averageSpeedKmh)
        assertEquals(5, telemetry.chargeCycle)
        assertEquals(2, telemetry.overflowDischarge)
    }

    @Test
    fun parse_readsTwoByteRealtimeFrames() {
        val frame = HexCodec.toByteArray("05 46 00 00 FA 04 D2 12 C0 01 2C 64 01 00 00 02")

        val telemetry = ScooterTelemetryParser.parse(frame)

        assertEquals(25.0f, telemetry!!.speedKmh!!)
        assertEquals(12.34f, telemetry.currentA)
        assertEquals(48.0f, telemetry.voltageV)
        assertEquals(30.0f, telemetry.remainingMileageKm)
        assertEquals(100, telemetry.batteryLevel)
        assertTrue(telemetry.charge == true)
        assertTrue(telemetry.lockState!!)
        assertEquals(2, telemetry.fault)
        assertEquals(2, telemetry.errorCode)
    }

    @Test
    fun parse_readsTwoByteCyclingDataFrames() {
        val frame = HexCodec.toByteArray("05 48 01 00 78 00 FA 00 01 E2 40")

        val telemetry = ScooterTelemetryParser.parse(frame, ProtocolFamily.TWO_BYTE)

        assertEquals(120, telemetry!!.timeOfRide)
        assertEquals(25.0f, telemetry.mileageOfRideKm)
        assertEquals(1234.56f, telemetry.totalMileageKm)
    }

    @Test
    fun extractSessionToken_readsTwoByteTokenResponse() {
        assertEquals("A1B2C3D4", ScooterTelemetryParser.extractSessionToken("060101A1B2C3D4"))
        assertNull(ScooterTelemetryParser.extractSessionToken("050101A1B2C3D4"))
    }

    @Test
    fun buffer_reassemblesSplitStatusFrame() {
        val buffer = ScooterTelemetryFrameBuffer()

        assertNull(buffer.append(HexCodec.toByteArray("43 00 0A")))
        val telemetry = buffer.append(
            HexCodec.toByteArray(
                "02 16 00 00 40 54 44 43 00 00 05 AB 64 D0 01 14 01 01 00 80 1E 00 5F 00 00 1E 15 0F 01 8B 01 F4 0C A3 D5 15 1D 0A"
            )
        )

        assertEquals("01.0 km/h", telemetry!!.formattedSpeed)
        assertTrue(telemetry.lightOn == true)
    }
}
