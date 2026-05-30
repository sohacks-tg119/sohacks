package com.hackerman.sohacksrev2

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object ScooterTransportCrypto {
    fun encryptHexToBytes(plainHex: String, aesKeyHex: String): ByteArray {
        val plainBytes = HexCodec.toByteArray(plainHex)
        return cipher(Cipher.ENCRYPT_MODE, aesKeyHex).doFinal(plainBytes.zeroPadToAesBlock())
    }

    fun decryptBytes(cipherBytes: ByteArray, aesKeyHex: String): ByteArray {
        return cipher(Cipher.DECRYPT_MODE, aesKeyHex).doFinal(cipherBytes)
    }

    private fun cipher(mode: Int, aesKeyHex: String): Cipher {
        val keyBytes = HexCodec.toByteArray(aesKeyHex)
        require(keyBytes.size == AES_BLOCK_SIZE) { "AES-Key muss 16 Byte lang sein" }
        return Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(mode, SecretKeySpec(keyBytes, "AES"))
        }
    }

    private fun ByteArray.zeroPadToAesBlock(): ByteArray {
        val paddedSize = ((size + AES_BLOCK_SIZE - 1) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE
        if (paddedSize == size) return this
        return copyOf(paddedSize)
    }

    private const val AES_BLOCK_SIZE = 16
}
