package com.hackerman.sohacksrev2

object HexCodec {
    fun normalize(hex: String): String = hex.replace("\\s".toRegex(), "").uppercase()

    fun toByteArray(hex: String): ByteArray {
        val cleaned = normalize(hex)
        require(cleaned.length % 2 == 0) { "Hex muss eine gerade Laenge haben" }
        require(cleaned.all { it in '0'..'9' || it in 'A'..'F' }) { "Hex enthaelt ungueltige Zeichen" }

        return ByteArray(cleaned.length / 2) { index ->
            cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
