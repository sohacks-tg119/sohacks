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

        assertEquals(2.1f, telemetry!!.speedKmh)
        assertEquals("02.1 km/h", telemetry.formattedSpeed)
        assertTrue(telemetry.lightOn)
    }

    @Test
    fun parse_readsLightOffAndZeroSpeed() {
        val frame = HexCodec.toByteArray(
            "42 00 00 02 16 00 00 40 54 44 43 00 00 05 AB 64 C5 01 14 01 01 00 80 1E"
        )

        val telemetry = ScooterTelemetryParser.parse(frame)

        assertEquals(0.0f, telemetry!!.speedKmh)
        assertEquals("00.0 km/h", telemetry.formattedSpeed)
        assertFalse(telemetry.lightOn)
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

        assertTrue(ScooterTelemetryParser.parse(lightOn)!!.lightOn)
        assertFalse(ScooterTelemetryParser.parse(lightOff)!!.lightOn)
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
        assertTrue(telemetry.lightOn)
    }
}
