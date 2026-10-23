package com.starter.security

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class StoredSecretMigrationTest {
    @Test fun `email credentials participate in encrypted storage and key rotation`() {
        val writes = mutableListOf<String>()
        val encryption = testEncryption()
        val jdbc = mock(JdbcTemplate::class.java) { call ->
            when (call.method.name) {
                "queryForList" -> if ((call.arguments[0] as String).contains("FROM email_settings")) listOf(mapOf("id" to 1L,
                    "smtp_username" to "private-user", "smtp_password" to "private-app-password", "api_key" to encryption.encrypt("private-api-key"))) else emptyList<Map<String, Any>>()
                "update" -> { writes += call.arguments[1] as String; 1 }
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
        StoredSecretMigration(jdbc, encryption).run(DefaultApplicationArguments())
        assertThat(writes.map { encryption.decrypt(it) }).containsExactly("private-user", "private-app-password", "private-api-key")
        assertThat(writes.all { it.startsWith("enc:v1:") && !it.contains("private-") }).isTrue()
    }
    @Test fun `legacy rows are encrypted before application runners use repositories`() {
        val writes = mutableListOf<String>()
        val jdbc = mock(JdbcTemplate::class.java) { call ->
            when (call.method.name) {
                "queryForList" -> if ((call.arguments[0] as String).contains("FROM app_settings")) listOf(mapOf("id" to UUID.randomUUID(), "storage_access_key" to "old-access", "storage_secret_key" to "old-secret")) else emptyList<Map<String, Any>>()
                "update" -> { writes += call.arguments[1] as String; 1 }
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
        val encryption = testEncryption()
        StoredSecretMigration(jdbc, encryption).run(DefaultApplicationArguments())
        assertThat(writes).hasSize(2)
        assertThat(writes.map { encryption.decrypt(it) }).containsExactly("old-access", "old-secret")
        assertThat(writes.all { it.startsWith("enc:v1:") }).isTrue()
    }
}
