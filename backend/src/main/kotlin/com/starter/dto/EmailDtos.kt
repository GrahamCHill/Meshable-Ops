package com.starter.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.starter.email.*
import jakarta.validation.constraints.*

data class EmailSettingsRequest(
    val source: EmailConfigurationSource,
    val enabled: Boolean = false,
    val provider: EmailProvider = EmailProvider.SMTP,
    @field:Size(max = 254) val fromAddress: String = "",
    @field:Size(max = 120) val fromName: String = "",
    @field:Size(max = 254) val replyTo: String = "",
    @field:Size(max = 253) val smtpHost: String = "",
    @field:Min(1) @field:Max(65535) val smtpPort: Int = 587,
    val smtpSecurity: SmtpSecurity = SmtpSecurity.STARTTLS,
    val smtpAuth: SmtpAuth = SmtpAuth.PASSWORD,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @field:Size(max = 512) val smtpUsername: String? = null,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @field:Size(max = 8192) val smtpPassword: String? = null,
    val clearSmtpCredentials: Boolean = false,
    @field:Size(max = 2000) val apiBaseUrl: String = "",
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @field:Size(max = 8192) val apiKey: String? = null,
    val clearApiKey: Boolean = false,
    @field:Size(max = 253) val mailgunDomain: String = "",
) {
    override fun toString() = "EmailSettingsRequest(source=$source, provider=$provider, enabled=$enabled)"
}

data class EmailConfigurationView(
    val enabled: Boolean, val provider: EmailProvider, val fromAddress: String, val fromName: String, val replyTo: String,
    val smtpHost: String, val smtpPort: Int, val smtpSecurity: SmtpSecurity, val smtpAuth: SmtpAuth,
    val smtpUsernameConfigured: Boolean, val smtpPasswordConfigured: Boolean,
    val apiBaseUrl: String, val apiKeyConfigured: Boolean, val mailgunDomain: String,
)
data class EmailSettingsResponse(val source: EmailConfigurationSource, val encryptionReady: Boolean, val admin: EmailConfigurationView, val environment: EmailConfigurationView)
data class EmailTestRequest(@field:NotBlank @field:Email @field:Size(max = 254) val recipient: String)
data class SendEmailRequest(
    @field:NotBlank @field:Email @field:Size(max = 254) val recipient: String,
    @field:NotBlank @field:Size(max = 200) val subject: String,
    @field:NotBlank @field:Size(max = 20000) val text: String,
    @field:Size(max = 100000) val html: String = "",
) {
    override fun toString() = "SendEmailRequest()"
}
data class EmailDeliveryResult(val status: String, val message: String)
