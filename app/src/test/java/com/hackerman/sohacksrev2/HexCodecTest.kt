package com.hackerman.sohacksrev2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HexCodecTest {
    @Test
    fun normalize_removesWhitespaceAndUppercases() {
        assertEquals("D706A30001AA", HexCodec.normalize("d7 06\na3 00 01 aa"))
    }

    @Test
    fun toByteArray_parsesValidHex() {
        assertArrayEquals(
            byteArrayOf(0xD7.toByte(), 0x06, 0xA3.toByte(), 0x00, 0x01, 0xAA.toByte()),
            HexCodec.toByteArray("D706A30001AA")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun toByteArray_rejectsOddLengthHex() {
        HexCodec.toByteArray("ABC")
    }

    @Test(expected = IllegalArgumentException::class)
    fun toByteArray_rejectsInvalidCharacters() {
        HexCodec.toByteArray("D706XX")
    }
}
