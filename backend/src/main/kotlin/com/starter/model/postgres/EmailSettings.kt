package com.starter.model.postgres

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.email.*
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*

@Entity
@Table(name = "email_settings")
class EmailSettings(
    @Id val id: Long = 1,
    @Enumerated(EnumType.STRING) var source: EmailConfigurationSource = EmailConfigurationSource.ENVIRONMENT,
    var enabled: Boolean = false,
    @Enumerated(EnumType.STRING) var provider: EmailProvider = EmailProvider.SMTP,
    var fromAddress: String = "",
    var fromName: String = "",
    var replyTo: String = "",
    var smtpHost: String = "",
    var smtpPort: Int = 587,
    @Enumerated(EnumType.STRING) var smtpSecurity: SmtpSecurity = SmtpSecurity.STARTTLS,
    @Enumerated(EnumType.STRING) var smtpAuth: SmtpAuth = SmtpAuth.PASSWORD,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(columnDefinition = "TEXT") var smtpUsername: String? = null,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(columnDefinition = "TEXT") var smtpPassword: String? = null,
    @Column(columnDefinition = "TEXT") var apiBaseUrl: String = "",
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(columnDefinition = "TEXT") var apiKey: String? = null,
    var mailgunDomain: String = "",
) {
    fun active() = EmailConfiguration(enabled, provider, fromAddress, fromName, replyTo, smtpHost, smtpPort, smtpSecurity, smtpAuth,
        smtpUsername, smtpPassword, apiBaseUrl.ifBlank { provider.defaultUrl }, apiKey, mailgunDomain)
    override fun toString() = "EmailSettings(id=$id, source=$source, provider=$provider)"
}
