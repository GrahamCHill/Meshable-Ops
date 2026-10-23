package com.starter.email

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.EmailSettingsRequest
import com.starter.model.postgres.EmailSettings
import com.starter.repository.postgres.EmailSettingsRepository
import com.starter.security.SecretEncryption
import com.starter.security.testEncryption
import com.starter.service.AuditLogService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class EmailSettingsServiceTest {
    private val repository = mock(EmailSettingsRepository::class.java)
    private val audit = mock(AuditLogService::class.java)
    private val environment = EmailEnvironmentProperties(smtpUsername = "env-user", smtpPassword = "env-password", apiKey = "env-api-key")
    private val saved = EmailSettings(source = EmailConfigurationSource.ADMIN, fromAddress = "sender@example.test", smtpHost = "smtp.example.test", smtpUsername = "saved-user", smtpPassword = "saved-password")
    private fun service(encryption: SecretEncryption = testEncryption()): EmailSettingsService {
        `when`(repository.findById(1)).thenReturn(Optional.of(saved))
        return EmailSettingsService(repository, environment, encryption, audit)
    }
    private fun smtp() = EmailSettingsRequest(EmailConfigurationSource.ADMIN, true, fromAddress = saved.fromAddress, smtpHost = saved.smtpHost)

    @Test fun `blank credentials retain only the same SMTP account and endpoint`() {
        val service = service()
        service.save(smtp(), "admin@example.test")
        assertThat(saved.smtpUsername).isEqualTo("saved-user")
        assertThat(saved.smtpPassword).isEqualTo("saved-password")
        assertThatThrownBy { service.save(smtp().copy(smtpHost = "other.example.test"), "admin@example.test") }.hasMessageContaining("Configure the SMTP")
        assertThatThrownBy { service.save(smtp().copy(smtpUsername = "other-user"), "admin@example.test") }.hasMessageContaining("Configure the SMTP")
        assertThat(saved.smtpHost).isEqualTo("smtp.example.test")
    }

    @Test fun `SMTP app passwords can be replaced and all credentials explicitly cleared`() {
        val service = service()
        service.save(smtp().copy(smtpPassword = "new-app-password"), "admin@example.test")
        assertThat(saved.smtpPassword).isEqualTo("new-app-password")
        service.save(smtp().copy(enabled = false, clearSmtpCredentials = true), "admin@example.test")
        assertThat(saved.smtpUsername).isNull(); assertThat(saved.smtpPassword).isNull()
    }

    @Test fun `environment and admin credentials remain separate and views are redacted`() {
        val service = service()
        assertThat(service.current().smtpPassword).isEqualTo("saved-password")
        service.save(EmailSettingsRequest(EmailConfigurationSource.ENVIRONMENT, smtpPassword = "not-copied"), "admin@example.test")
        assertThat(service.current().smtpPassword).isEqualTo("env-password")
        assertThat(saved.smtpPassword).isEqualTo("saved-password")
        val json = jacksonObjectMapper().writeValueAsString(service.settings())
        assertThat(json).doesNotContain("env-user", "env-password", "env-api-key", "saved-user", "saved-password", "not-copied")
        assertThat(json).contains("smtpUsernameConfigured", "smtpPasswordConfigured", "apiKeyConfigured")
    }

    @Test fun `provider endpoint and Mailgun domain changes never forward stored keys`() {
        val service = service()
        fun api(provider: EmailProvider, url: String = provider.defaultUrl, domain: String = "") =
            EmailSettingsRequest(EmailConfigurationSource.ADMIN, true, provider, saved.fromAddress, apiBaseUrl = url, mailgunDomain = domain)
        service.save(api(EmailProvider.RESEND).copy(apiKey = "resend-key"), "admin@example.test")
        service.save(api(EmailProvider.RESEND), "admin@example.test")
        assertThat(saved.apiKey).isEqualTo("resend-key")
        assertThat(saved.smtpPassword).isNull()
        assertThatThrownBy { service.save(api(EmailProvider.SENDGRID), "admin@example.test") }.hasMessageContaining("API key")
        assertThatThrownBy { service.save(api(EmailProvider.RESEND, "https://other.example.test"), "admin@example.test") }.hasMessageContaining("API key")
        service.save(api(EmailProvider.MAILGUN, domain = "mg.example.test").copy(apiKey = "mailgun-key"), "admin@example.test")
        assertThatThrownBy { service.save(api(EmailProvider.MAILGUN, domain = "other.example.test"), "admin@example.test") }.hasMessageContaining("API key")
        service.save(api(EmailProvider.MAILGUN, domain = "mg.example.test").copy(enabled = false, clearApiKey = true), "admin@example.test")
        assertThat(saved.apiKey).isNull()
    }

    @Test fun `saving credentials requires a wrapping key and insecure transports are rejected`() {
        val service = service(SecretEncryption(""))
        assertThatThrownBy { service.save(smtp(), "admin@example.test") }.hasMessageContaining("APP_SECRETS_ENCRYPTION_KEY").hasMessageNotContaining("saved-password")
        assertThatThrownBy { service.save(smtp().copy(smtpSecurity = SmtpSecurity.NONE), "admin@example.test") }.hasMessageContaining("requires STARTTLS or TLS")
        listOf("http://remote.example.test", "https://user:secret@example.test", "https://api.example.test?key=secret", "file:///tmp/mail").forEach { url ->
            assertThatThrownBy { service.save(EmailSettingsRequest(EmailConfigurationSource.ADMIN, provider = EmailProvider.RESEND, apiBaseUrl = url), "admin@example.test") }
                .hasMessageContaining("HTTPS").hasMessageNotContaining("secret")
        }
        verify(repository, never()).saveAndFlush(any(EmailSettings::class.java))
    }

    @Test fun `mailboxes and sender names cannot inject headers or multiple recipients`() {
        listOf("a@example.test,b@example.test", "Name <a@example.test>", "a@example.test\r\nBcc: b@example.test").forEach { address ->
            assertThatThrownBy { validateMailbox(address, "sender") }.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        }
        assertThatThrownBy { validateEmailConfiguration(saved.active().copy(fromName = "Sender\r\nBcc: someone")) }.isInstanceOf(com.starter.exception.BadRequestException::class.java)
    }

    @Test fun `email request entity active config and environment debug output never expose credentials`() {
        val mapper = jacksonObjectMapper()
        val request = mapper.readValue("""{"source":"ADMIN","smtpUsername":"private-user","smtpPassword":"private-password","apiKey":"private-key"}""", EmailSettingsRequest::class.java)
        assertThat(request.smtpPassword).isEqualTo("private-password")
        assertThat(request.smtpUsername).isEqualTo("private-user")
        assertThat(request.apiKey).isEqualTo("private-key")
        listOf(request, EmailSettings(smtpUsername = "private-user", smtpPassword = "private-password", apiKey = "private-key"),
            EmailConfiguration(smtpUsername = "private-user", smtpPassword = "private-password", apiKey = "private-key"),
            EmailEnvironmentProperties(smtpUsername = "private-user", smtpPassword = "private-password", apiKey = "private-key")).forEach {
            assertThat(mapper.writeValueAsString(it)).doesNotContain("private-")
            assertThat(it.toString()).doesNotContain("private-")
        }
        assertThat(environment.toString()).doesNotContain("env-password", "env-api-key", "env-user")
    }
}
