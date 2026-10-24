package com.starter.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun secret(): String = encode(ByteArray(20).also(SecureRandom()::nextBytes))
    internal fun encode(bytes: ByteArray): String {
        var buffer = 0; var bits = 0
        return buildString {
            bytes.forEach { byte -> buffer = (buffer shl 8) or (byte.toInt() and 255); bits += 8
                while (bits >= 5) { bits -= 5; append(ALPHABET[(buffer ushr bits) and 31]) }
            }
            if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
        }
    }
    private fun decode(value: String): ByteArray {
        var buffer = 0; var bits = 0; val output = mutableListOf<Byte>()
        value.forEach { char -> val digit = ALPHABET.indexOf(char); require(digit >= 0)
            buffer = (buffer shl 5) or digit; bits += 5
            if (bits >= 8) { bits -= 8; output += ((buffer ushr bits) and 255).toByte() }
        }
        return output.toByteArray()
    }
    fun code(secret: String, step: Long, digits: Int = 6): String {
        val digest = Mac.getInstance("HmacSHA1").run {
            init(SecretKeySpec(decode(secret), "HmacSHA1")); doFinal(ByteBuffer.allocate(8).putLong(step).array())
        }
        val offset = digest.last().toInt() and 15
        val number = ByteBuffer.wrap(digest, offset, 4).int and Int.MAX_VALUE
        val modulus = if (digits == 8) 100_000_000 else 1_000_000
        return (number % modulus).toString().padStart(digits, '0')
    }
    fun verify(secret: String, code: String, lastStep: Long, epochSeconds: Long): Long? {
        if (!Regex("[0-9]{6}").matches(code)) return null
        val step = epochSeconds / 30
        return listOf(step, step - 1, step + 1).firstOrNull { it > lastStep && SecureValues.equal(code(secret, it), code) }
    }
}
