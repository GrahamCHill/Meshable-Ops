package com.starter.security

import com.starter.exception.BadRequestException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The wrapping keys stay outside PostgreSQL. Each write gets its own authenticated nonce. */
@Component
class SecretEncryption(
    @Value("\${app.secrets.encryption-key:}") primary: String,
    @Value("\${app.secrets.previous-encryption-key:}") previous: String = "",
) {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private fun decodeKey(value: String): ByteArray? {
        if (value.isBlank()) return null
        val bytes = runCatching { Base64.getDecoder().decode(value.trim()) }.getOrNull()
        require(bytes?.size == 32) { "APP_SECRETS_ENCRYPTION_KEY must be a base64-encoded 32-byte key" }
        return bytes
    }
    private val primaryKey = decodeKey(primary)
    private val previousKey = decodeKey(previous)
    private fun keyId(key: ByteArray) = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(key)).take(16)
    val configured: Boolean get() = primaryKey != null

    fun requireConfigured() {
        if (!configured) throw BadRequestException("Set APP_SECRETS_ENCRYPTION_KEY on the server before saving credentials")
    }

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        requireConfigured()
        val key = primaryKey!!
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = "enc:v1:${keyId(key)}"
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header.toByteArray(Charsets.UTF_8))
        return "$header:${encoder.encodeToString(nonce)}:${encoder.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))}"
    }

    fun decrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        try {
            val parts = value.split(':')
            require(parts.size == 5 && parts[0] == "enc" && parts[1] == "v1")
            val key = listOfNotNull(primaryKey, previousKey).firstOrNull { keyId(it) == parts[2] }
                ?: error("No matching wrapping key")
            val nonce = decoder.decode(parts[3])
            require(nonce.size == 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(parts.take(3).joinToString(":").toByteArray(Charsets.UTF_8))
            return String(cipher.doFinal(decoder.decode(parts[4])), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalStateException("Cannot unlock a stored credential. Check the server encryption keys.")
        }
    }

    /** Only the startup migration accepts old plaintext; ordinary entity reads fail closed. */
    fun migrate(value: String?): String? = when {
        value.isNullOrEmpty() -> null
        value.startsWith("enc:") -> encrypt(decrypt(value))
        else -> encrypt(value)
    }
}
