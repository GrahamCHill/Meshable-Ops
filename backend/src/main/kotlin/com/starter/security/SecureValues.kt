package com.starter.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class SecureValues(@Value("\${jwt.secret}") private val pepper: String) {
    private val random = SecureRandom()
    fun token(bytes: Int = 32): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))
    fun code(): String = random.nextInt(1_000_000).toString().padStart(6, '0')
    fun proof(value: String): String = hmac(pepper, value)
    companion object {
        fun hash(value: String) = hash(value.toByteArray(Charsets.UTF_8))
        fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
        fun hmac(secret: String, value: String): String = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
        fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}
