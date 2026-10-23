package com.starter.email

import com.starter.exception.BadRequestException
import jakarta.mail.internet.InternetAddress
import java.net.URI

internal fun validateMailbox(value: String, label: String) {
    if (value.length > 254 || value.any { it.isISOControl() }) throw BadRequestException("Enter a valid $label email address")
    val address = runCatching { InternetAddress(value, true).also { it.validate() } }.getOrNull()
    if (address == null || address.address != value || !value.contains('@') || address.isGroup || address.personal != null)
        throw BadRequestException("Enter a single $label email address without a display name")
}

internal fun validateEmailConfiguration(config: EmailConfiguration) {
    if (config.fromName.length > 120 || config.fromName.any { it.isISOControl() }) throw BadRequestException("Use a sender name without control characters")
    if (config.enabled || config.fromAddress.isNotBlank()) validateMailbox(config.fromAddress, "sender")
    if (config.replyTo.isNotBlank()) validateMailbox(config.replyTo, "reply-to")
    if (config.provider == EmailProvider.SMTP) {
        if (config.smtpHost.isNotBlank() && !Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?").matches(config.smtpHost))
            throw BadRequestException("Enter an SMTP hostname without a protocol, port, or credentials")
        if (config.smtpPort !in 1..65535) throw BadRequestException("Choose an SMTP port between 1 and 65535")
        if (config.smtpAuth != SmtpAuth.NONE && config.smtpSecurity == SmtpSecurity.NONE)
            throw BadRequestException("SMTP authentication requires STARTTLS or TLS")
        if (config.enabled && config.smtpHost.isBlank()) throw BadRequestException("Enter the SMTP host")
        if (config.smtpUsername?.any { it.isISOControl() } == true) throw BadRequestException("Invalid SMTP username")
        if (config.enabled && config.smtpAuth != SmtpAuth.NONE && (config.smtpUsername.isNullOrBlank() || config.smtpPassword.isNullOrBlank()))
            throw BadRequestException("Configure the SMTP username and password, app password, or OAuth2 access token")
    } else {
        val url = runCatching { URI(config.apiBaseUrl) }.getOrNull()
        if (url == null || url.host == null || url.userInfo != null || url.query != null || url.fragment != null ||
            url.scheme !in setOf("https", "http") || (url.scheme == "http" && url.host !in setOf("localhost", "127.0.0.1", "::1", "[::1]")))
            throw BadRequestException("Use an HTTPS email API base URL without credentials, query parameters, or fragments")
        if (config.apiKey?.any { it.isISOControl() } == true) throw BadRequestException("Invalid email API key")
        if (config.enabled && config.apiKey.isNullOrBlank()) throw BadRequestException("Configure the email service API key")
        if (config.provider == EmailProvider.MAILGUN && (config.enabled || config.mailgunDomain.isNotBlank()) &&
            !Regex("(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?").matches(config.mailgunDomain))
            throw BadRequestException("Enter the verified Mailgun sending domain")
    }
}
