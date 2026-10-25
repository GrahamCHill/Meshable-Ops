package com.starter.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.*
import com.starter.model.postgres.AiSettings
import com.starter.repository.postgres.AiSettingsRepository
import com.starter.security.SecretEncryption
import com.starter.security.testEncryption
import com.starter.service.AuditLogService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class AiSettingsServiceTest {
    private val repository = mock(AiSettingsRepository::class.java)
    private val environment = AiEnvironmentProperties(chat = AiEnvironmentProperties.EndpointProperties(apiKey = "environment-private-key"))
    private val audit = mock(AuditLogService::class.java)
    private fun request(endpoint: AiEndpointRequest) = AiSettingsRequest("ADMIN", true, true, endpoint, AiEndpointRequest(AiProvider.LOCAL))

    @Test fun `search launcher and AI answer switches persist independently`() {
        val stored = AiSettings(source = "ADMIN")
        `when`(repository.findById(1)).thenReturn(Optional.of(stored))
        val service = AiSettingsService(repository, environment, testEncryption(), audit)
        val request = request(AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, stored.chatBaseUrl, "model"))
        service.save(request.copy(enabled = false, searchEnabled = true, launcherEnabled = false), "admin@test.local")
        assertThat(service.status().searchEnabled).isTrue()
        assertThat(service.status().launcherEnabled).isFalse()
        assertThat(service.status().enabled).isFalse()
        assertThat(service.status().dimensions).isEqualTo(1536)
        service.save(request.copy(searchEnabled = false, launcherEnabled = true), "admin@test.local")
        assertThat(service.status().searchEnabled).isFalse()
        assertThat(service.status().launcherEnabled).isFalse()
        assertThat(service.status().enabled).isFalse()
    }

    @Test fun `disabled chat configurations still reject credentials embedded in URLs and insecure key transport`() {
        `when`(repository.findById(1)).thenReturn(Optional.empty())
        val service = AiSettingsService(repository, environment, testEncryption(), audit)
        listOf(
            AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, "https://name:private-key@provider.test/v1"),
            AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1?api_key=private-key"),
            AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, "http://provider.test/v1", apiKey = "private-key"),
        ).forEach { endpoint ->
            assertThatThrownBy { service.save(request(endpoint).copy(enabled = false), "admin@test.local") }
                .isInstanceOf(com.starter.exception.BadRequestException::class.java)
                .hasMessageNotContaining("private-key")
        }
        verify(repository, never()).saveAndFlush(any(AiSettings::class.java))
    }

    @Test fun `admin responses redact all keys and blanks preserve only a key bound to the same endpoint`() {
        val stored = AiSettings(source = "ADMIN", chatModel = "model", chatApiKey = "stored-private-key")
        `when`(repository.findById(1)).thenReturn(Optional.of(stored))
        val service = AiSettingsService(repository, environment, testEncryption(), audit)
        val response = service.save(request(AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, stored.chatBaseUrl, "model")), "admin@test.local")
        assertThat(stored.chatApiKey).isEqualTo("stored-private-key")
        assertThat(response.admin.chat.apiKeyConfigured).isTrue()
        assertThat(jacksonObjectMapper().writeValueAsString(response)).doesNotContain("stored-private-key", "environment-private-key")
        service.save(request(AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, "https://other.test/v1", "model")), "admin@test.local")
        assertThat(stored.chatApiKey).isNull()
        assertThat(service.current().chat.apiKey).isNull()
    }
    @Test fun `environment and admin sources do not borrow each others credentials`() {
        val stored = AiSettings(source = "ENVIRONMENT", chatApiKey = "stored-private-key")
        `when`(repository.findById(1)).thenReturn(Optional.of(stored))
        val service = AiSettingsService(repository, environment, testEncryption(), audit)
        assertThat(service.current().chat.apiKey).isEqualTo("environment-private-key")
        stored.source = "ADMIN"
        assertThat(service.current().chat.apiKey).isEqualTo("stored-private-key")
        service.save(request(AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, stored.chatBaseUrl, "model", clearApiKey = true)), "admin@test.local")
        assertThat(stored.chatApiKey).isNull()
    }
    @Test fun `saving a new key requires a server encryption key`() {
        `when`(repository.findById(1)).thenReturn(Optional.empty())
        val service = AiSettingsService(repository, environment, SecretEncryption(""), audit)
        assertThatThrownBy { service.save(request(AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "model", "new-private-key")), "admin@test.local") }
            .hasMessageContaining("APP_SECRETS_ENCRYPTION_KEY").hasMessageNotContaining("new-private-key")
        verify(repository, never()).saveAndFlush(any(AiSettings::class.java))
    }
    @Test fun `draft model checks use only the selected sources endpoint bound key without saving`() {
        val stored = AiSettings(source = "ENVIRONMENT", chatApiKey = "admin-private-key")
        `when`(repository.findById(1)).thenReturn(Optional.of(stored))
        val service = AiSettingsService(repository, environment, testEncryption(), audit)
        val draft = AiEndpointCheckRequest("ADMIN", AiEndpointRequest(AiProvider.OPENAI_COMPATIBLE, stored.chatBaseUrl))
        assertThat(service.endpointForCheck("chat", draft).apiKey).isEqualTo("admin-private-key")
        assertThat(service.endpointForCheck("chat", draft.copy(endpoint = draft.endpoint.copy(baseUrl = "https://other.test/v1"))).apiKey).isNull()
        assertThat(service.endpointForCheck("chat", draft.copy(endpoint = draft.endpoint.copy(clearApiKey = true))).apiKey).isNull()
        assertThat(service.endpointForCheck("chat", draft.copy(endpoint = draft.endpoint.copy(apiKey = "new-private-key"))).apiKey).isEqualTo("new-private-key")
        val fromEnvironment = service.endpointForCheck("chat", draft.copy(source = "ENVIRONMENT", endpoint = draft.endpoint.copy(baseUrl = "https://other.test/v1", apiKey = "ignored-key")))
        assertThat(fromEnvironment.apiKey).isEqualTo("environment-private-key")
        assertThat(fromEnvironment.baseUrl).isEqualTo(environment.chat.baseUrl)
        verify(repository, never()).saveAndFlush(any(AiSettings::class.java))
        verifyNoInteractions(audit)
    }
}
