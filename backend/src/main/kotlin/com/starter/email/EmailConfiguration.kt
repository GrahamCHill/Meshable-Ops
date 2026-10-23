package com.starter.email

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

enum class EmailProvider(val defaultUrl: String) {
    SMTP(""), RESEND("https://api.resend.com"), SENDGRID("https://api.sendgrid.com"), MAILGUN("https://api.mailgun.net/v3")
}
enum class SmtpSecurity { STARTTLS, TLS, NONE }
enum class SmtpAuth { PASSWORD, OAUTH2, NONE }
enum class EmailConfigurationSource { ENVIRONMENT, ADMIN }

data class EmailConfiguration(
    val enabled: Boolean = false,
    val provider: EmailProvider = EmailProvider.SMTP,
    val fromAddress: String = "",
    val fromName: String = "",
    val replyTo: String = "",
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val smtpSecurity: SmtpSecurity = SmtpSecurity.STARTTLS,
    val smtpAuth: SmtpAuth = SmtpAuth.PASSWORD,
    @get:JsonIgnore val smtpUsername: String? = null,
    @get:JsonIgnore val smtpPassword: String? = null,
    val apiBaseUrl: String = "",
    @get:JsonIgnore val apiKey: String? = null,
    val mailgunDomain: String = "",
) {
    override fun toString() = "EmailConfiguration(enabled=$enabled, provider=$provider)"
}

@ConfigurationProperties("app.email")
data class EmailEnvironmentProperties(
    var enabled: Boolean = false,
    var provider: EmailProvider = EmailProvider.SMTP,
    var fromAddress: String = "",
    var fromName: String = "",
    var replyTo: String = "",
    var smtpHost: String = "",
    var smtpPort: Int = 587,
    var smtpSecurity: SmtpSecurity = SmtpSecurity.STARTTLS,
    var smtpAuth: SmtpAuth = SmtpAuth.PASSWORD,
    @get:JsonIgnore var smtpUsername: String = "",
    @get:JsonIgnore var smtpPassword: String = "",
    var apiBaseUrl: String = "",
    @get:JsonIgnore var apiKey: String = "",
    var mailgunDomain: String = "",
) {
    fun active() = EmailConfiguration(enabled, provider, fromAddress.trim(), fromName.trim(), replyTo.trim(), smtpHost.trim(), smtpPort,
        smtpSecurity, smtpAuth, smtpUsername.ifBlank { null }, smtpPassword.ifBlank { null },
        apiBaseUrl.trim().trimEnd('/').ifBlank { provider.defaultUrl }, apiKey.ifBlank { null }, mailgunDomain.trim())
    override fun toString() = "EmailEnvironmentProperties(enabled=$enabled, provider=$provider)"
}

@Configuration
@EnableConfigurationProperties(EmailEnvironmentProperties::class)
class EmailConfig
