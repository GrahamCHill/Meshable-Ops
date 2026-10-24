package com.starter.email

import com.starter.dto.*
import com.starter.model.postgres.EmailSettings
import com.starter.repository.postgres.EmailSettingsRepository
import com.starter.security.SecretEncryption
import com.starter.service.AuditLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EmailSettingsService(
    private val repository: EmailSettingsRepository,
    private val environment: EmailEnvironmentProperties,
    private val encryption: SecretEncryption,
    private val audit: AuditLogService,
    private val mfaGuard: com.starter.mfa.EmailMfaGuard,
) {
    @Transactional(readOnly = true)
    fun current(): EmailConfiguration = repository.findById(1).orElse(null)?.takeIf { it.source == EmailConfigurationSource.ADMIN }?.active() ?: environment.active()

    @Transactional(readOnly = true)
    fun settings(): EmailSettingsResponse = response(repository.findById(1).orElseGet { EmailSettings() })

    @Transactional
    fun save(request: EmailSettingsRequest, actor: String): EmailSettingsResponse {
        val saved = repository.findById(1).orElseGet { EmailSettings() }
        if (request.source == EmailConfigurationSource.ADMIN) {
            val previous = saved.active()
            val sameSmtp = previous.provider == request.provider && request.smtpHost.trim().equals(previous.smtpHost, true) &&
                request.smtpPort == previous.smtpPort && request.smtpSecurity == previous.smtpSecurity && request.smtpAuth == previous.smtpAuth
            val username = request.smtpUsername?.ifBlank { null } ?: previous.smtpUsername.takeIf { sameSmtp }
            val password = request.smtpPassword?.ifBlank { null } ?: previous.smtpPassword.takeIf { sameSmtp && username == previous.smtpUsername }
            val url = request.apiBaseUrl.trim().trimEnd('/').ifBlank { request.provider.defaultUrl }
            val sameApi = request.provider == previous.provider && url == previous.apiBaseUrl && request.mailgunDomain.trim() == previous.mailgunDomain
            val key = request.apiKey?.ifBlank { null } ?: previous.apiKey.takeIf { sameApi }
            val smtp = request.provider == EmailProvider.SMTP && request.smtpAuth != SmtpAuth.NONE && !request.clearSmtpCredentials
            val config = EmailConfiguration(request.enabled, request.provider, request.fromAddress.trim(), request.fromName.trim(), request.replyTo.trim(),
                request.smtpHost.trim(), request.smtpPort, request.smtpSecurity, request.smtpAuth,
                username.takeIf { smtp }, password.takeIf { smtp }, url,
                key.takeIf { request.provider != EmailProvider.SMTP && !request.clearApiKey }, request.mailgunDomain.trim())
            validateEmailConfiguration(config)
            mfaGuard.check(config)
            if (listOf(config.smtpUsername, config.smtpPassword, config.apiKey).any { !it.isNullOrBlank() }) encryption.requireConfigured()
            saved.enabled = config.enabled; saved.provider = config.provider
            saved.fromAddress = config.fromAddress; saved.fromName = config.fromName; saved.replyTo = config.replyTo
            saved.smtpHost = config.smtpHost; saved.smtpPort = config.smtpPort; saved.smtpSecurity = config.smtpSecurity; saved.smtpAuth = config.smtpAuth
            saved.smtpUsername = config.smtpUsername; saved.smtpPassword = config.smtpPassword
            saved.apiBaseUrl = config.apiBaseUrl; saved.apiKey = config.apiKey; saved.mailgunDomain = config.mailgunDomain
        }
        if (request.source == EmailConfigurationSource.ENVIRONMENT) mfaGuard.check(environment.active())
        saved.source = request.source
        repository.saveAndFlush(saved)
        audit.record(actingEmail = actor, action = "EMAIL_SETTINGS_UPDATED", resourceType = "EMAIL_SETTINGS", resourceId = "1")
        return response(saved)
    }

    private fun response(saved: EmailSettings) = EmailSettingsResponse(saved.source, encryption.configured, view(saved.active()), view(environment.active()))
    private fun view(c: EmailConfiguration) = EmailConfigurationView(c.enabled, c.provider, c.fromAddress, c.fromName, c.replyTo,
        c.smtpHost, c.smtpPort, c.smtpSecurity, c.smtpAuth, !c.smtpUsername.isNullOrBlank(), !c.smtpPassword.isNullOrBlank(),
        c.apiBaseUrl, !c.apiKey.isNullOrBlank(), c.mailgunDomain)
}
