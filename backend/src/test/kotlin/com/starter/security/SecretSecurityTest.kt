package com.starter.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.AppSettings
import com.starter.model.postgres.StorageConnection
import com.starter.model.postgres.User
import com.starter.exception.BadRequestException
import com.starter.dto.AiEndpointRequest
import com.starter.dto.AdminSettingsRequest
import com.starter.dto.StorageConnectionRequest
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

class SecretSecurityTest {
    @Test fun `write only credential fields accept incoming JSON but never serialize back`() {
        val mapper = jacksonObjectMapper()
        val ai = mapper.readValue("""{"provider":"OPENAI_COMPATIBLE","apiKey":"private-api-key"}""", AiEndpointRequest::class.java)
        val storage = mapper.readValue("""{"storageAccessKey":"private-access","storageSecretKey":"private-secret"}""", AdminSettingsRequest::class.java)
        val connection = mapper.readValue("""{"nickname":"Archive","provider":"S3","bucket":"docs","accessKey":"private-access","secretKey":"private-secret"}""", StorageConnectionRequest::class.java)
        assertThat(ai.apiKey).isEqualTo("private-api-key")
        assertThat(storage.storageSecretKey).isEqualTo("private-secret")
        assertThat(connection.accessKey).isEqualTo("private-access")
        listOf(ai, storage, connection).forEach { value ->
            assertThat(mapper.writeValueAsString(value)).doesNotContain("private-")
            assertThat(value.toString()).doesNotContain("private-")
        }
    }
    @Test fun `encryption has independent nonces and detects tampering and wrong keys`() {
        val encryption = testEncryption()
        val first = encryption.encrypt("private-api-key")!!
        assertThat(first).startsWith("enc:v1:").doesNotContain("private-api-key").isNotEqualTo(encryption.encrypt("private-api-key"))
        assertThat(encryption.decrypt(first)).isEqualTo("private-api-key")
        assertThatThrownBy { testEncryption(8).decrypt(first) }.isInstanceOf(IllegalStateException::class.java).hasMessageNotContaining(first)
        val pieces = first.split(':').toMutableList()
        val data = Base64.getUrlDecoder().decode(pieces.last()); data[0] = (data[0].toInt() xor 1).toByte()
        pieces[4] = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        assertThatThrownBy { encryption.decrypt(pieces.joinToString(":")) }.isInstanceOf(IllegalStateException::class.java)
    }
    @Test fun `missing keys fail closed and plaintext is accepted only by migration`() {
        assertThatThrownBy { SecretEncryption("").encrypt("private-key") }.isInstanceOf(BadRequestException::class.java)
        val encryption = testEncryption()
        assertThatThrownBy { encryption.decrypt("old-plaintext") }.isInstanceOf(IllegalStateException::class.java)
        assertThat(encryption.decrypt(encryption.migrate("old-plaintext"))).isEqualTo("old-plaintext")
        val converter = EncryptedSecretConverter(encryption)
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("api-key"))).isEqualTo("api-key")
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
    }
    @Test fun `previous wrapping keys support rotation`() {
        val old = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        val fresh = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
        val rotated = SecretEncryption(fresh, old).migrate(SecretEncryption(old).encrypt("rotated-key"))
        assertThat(SecretEncryption(fresh).decrypt(rotated)).isEqualTo("rotated-key")
        assertThatThrownBy { SecretEncryption(old).decrypt(rotated) }.isInstanceOf(IllegalStateException::class.java)
    }
    @Test fun `entity JSON and debug strings redact credentials`() {
        listOf(AppSettings(storageAccessKey = "private-access", storageSecretKey = "private-secret"),
            StorageConnection(accessKey = "private-access", secretKey = "private-secret"), User(password = "private-password-hash")).forEach { value ->
            assertThat(jacksonObjectMapper().findAndRegisterModules().writeValueAsString(value)).doesNotContain("private-access", "private-secret", "private-password-hash")
            assertThat(value.toString()).doesNotContain("private-access", "private-secret", "private-password-hash")
        }
    }
    @Test fun `bcrypt hashes have independent salts cost 12 and reject UTF8 truncation`() {
        val encoder = SaltedPasswordEncoder()
        val first = encoder.encode("correct-horse-battery")
        assertThat(first).startsWith("\$2a\$12\$").isNotEqualTo(encoder.encode("correct-horse-battery")).doesNotContain("correct-horse")
        assertThat(encoder.matches("correct-horse-battery", first)).isTrue()
        assertThat(encoder.matches("wrong-password", first)).isFalse()
        assertThatThrownBy { encoder.encode("é".repeat(37)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(encoder.matches("a".repeat(73), first)).isFalse()
    }
}
