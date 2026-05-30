package com.hackerman.sohacksrev2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ScooterTransportCryptoTest {
    @Test
    fun encryptAndDecrypt_roundTripsZeroPaddedPayload() {
        val key = "30572F52364B3F473050415811632D2B"
        val plainHex = "D706A30001AA"

        val encrypted = ScooterTransportCrypto.encryptHexToBytes(plainHex, key)
        val decrypted = ScooterTransportCrypto.decryptBytes(encrypted, key)

        assertEquals(16, encrypted.size)
        assertArrayEquals(HexCodec.toByteArray(plainHex).copyOf(16), decrypted)
    }
}
