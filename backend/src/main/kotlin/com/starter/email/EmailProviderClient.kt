package com.starter.email

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.SendEmailRequest
import jakarta.mail.internet.InternetAddress
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import org.springframework.core.io.ByteArrayResource
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID

class EmailDeliveryException(message: String) : RuntimeException(message)

@Component
class EmailProviderClient(private val mapper: ObjectMapper) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()

    fun send(config: EmailConfiguration, message: SendEmailRequest, attachments: List<EmailAttachment> = emptyList()) {
        validateEmailConfiguration(config)
        if (config.provider == EmailProvider.SMTP) sendSmtp(config, message, attachments) else sendApi(config, message, attachments)
    }

    internal fun smtpSender(config: EmailConfiguration) = JavaMailSenderImpl().apply {
        host = config.smtpHost; port = config.smtpPort; defaultEncoding = "UTF-8"
        if (config.smtpAuth != SmtpAuth.NONE) { username = config.smtpUsername; password = config.smtpPassword }
        javaMailProperties.apply {
            setProperty("mail.smtp.auth", (config.smtpAuth != SmtpAuth.NONE).toString())
            setProperty("mail.smtp.starttls.enable", (config.smtpSecurity == SmtpSecurity.STARTTLS).toString())
            setProperty("mail.smtp.starttls.required", (config.smtpSecurity == SmtpSecurity.STARTTLS).toString())
            setProperty("mail.smtp.ssl.enable", (config.smtpSecurity == SmtpSecurity.TLS).toString())
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.connectiontimeout", "10000")
            setProperty("mail.smtp.timeout", "15000")
            setProperty("mail.smtp.writetimeout", "15000")
            setProperty("mail.debug", "false")
            if (config.smtpAuth == SmtpAuth.OAUTH2) setProperty("mail.smtp.auth.mechanisms", "XOAUTH2")
        }
    }

    private fun sendSmtp(config: EmailConfiguration, message: SendEmailRequest, attachments: List<EmailAttachment>) {
        try {
            val sender = smtpSender(config)
            val mime = sender.createMimeMessage()
            MimeMessageHelper(mime, attachments.isNotEmpty(), "UTF-8").apply {
                setFrom(config.fromAddress, config.fromName)
                setTo(message.recipient)
                if (config.replyTo.isNotBlank()) setReplyTo(config.replyTo)
                setSubject(message.subject); setText(message.text, false)
                attachments.forEach { addAttachment(it.filename, ByteArrayResource(it.bytes), "application/pdf") }
            }
            sender.send(mime)
        } catch (_: MailAuthenticationException) {
            throw EmailDeliveryException("SMTP authentication failed. Check the account, app password or access token, and provider SMTP access.")
        } catch (_: Exception) {
            throw EmailDeliveryException("SMTP delivery failed. Check the host, port, TLS certificate, sender permissions, and server network access.")
        }
    }

    private fun sendApi(config: EmailConfiguration, message: SendEmailRequest, attachments: List<EmailAttachment>) {
        val from = InternetAddress(config.fromAddress, config.fromName, "UTF-8").toUnicodeString()
        val builder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(25))
        val route: String
        val content: ByteArray
        when (config.provider) {
            EmailProvider.RESEND -> {
                route = "/emails"
                val body = mutableMapOf<String, Any>("from" to from, "to" to listOf(message.recipient), "subject" to message.subject, "text" to message.text)
                if (config.replyTo.isNotBlank()) body["reply_to"] = config.replyTo
                if (attachments.isNotEmpty()) body["attachments"] = attachments.map { mapOf("filename" to it.filename, "content" to Base64.getEncoder().encodeToString(it.bytes)) }
                content = mapper.writeValueAsBytes(body)
                builder.header("Content-Type", "application/json").header("Authorization", "Bearer ${config.apiKey}")
            }
            EmailProvider.SENDGRID -> {
                route = "/v3/mail/send"
                val body = mutableMapOf<String, Any>(
                    "from" to mapOf("email" to config.fromAddress, "name" to config.fromName),
                    "personalizations" to listOf(mapOf("to" to listOf(mapOf("email" to message.recipient)))),
                    "subject" to message.subject, "content" to listOf(mapOf("type" to "text/plain", "value" to message.text)))
                if (config.replyTo.isNotBlank()) body["reply_to"] = mapOf("email" to config.replyTo)
                if (attachments.isNotEmpty()) body["attachments"] = attachments.map { mapOf("filename" to it.filename, "content" to Base64.getEncoder().encodeToString(it.bytes), "type" to "application/pdf", "disposition" to "attachment") }
                content = mapper.writeValueAsBytes(body)
                builder.header("Content-Type", "application/json").header("Authorization", "Bearer ${config.apiKey}")
            }
            EmailProvider.MAILGUN -> {
                route = "/${config.mailgunDomain}/messages"
                val boundary = "email-${UUID.randomUUID()}"
                val fields = linkedMapOf("from" to from, "to" to message.recipient, "subject" to message.subject, "text" to message.text)
                if (config.replyTo.isNotBlank()) fields["h:Reply-To"] = config.replyTo
                content = ByteArrayOutputStream().use { output ->
                    fields.forEach { (key, value) -> output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8)) }
                    attachments.forEach { attachment ->
                        output.write("--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${attachment.filename}\"\r\nContent-Type: application/pdf\r\n\r\n".toByteArray(Charsets.UTF_8))
                        output.write(attachment.bytes); output.write("\r\n".toByteArray())
                    }
                    output.write("--$boundary--\r\n".toByteArray()); output.toByteArray()
                }
                builder.header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("api:${config.apiKey}".toByteArray(Charsets.UTF_8))}")
            }
            else -> error("Unsupported API provider")
        }
        try {
            val request = builder.uri(URI(config.apiBaseUrl.trimEnd('/') + route)).POST(HttpRequest.BodyPublishers.ofByteArray(content)).build()
            val status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            if (status !in 200..299) throw EmailDeliveryException(when (status) {
                401, 403 -> "Email service authentication or sender verification failed. Check the API key and verified sending address/domain."
                429 -> "The email service is rate limited or out of quota. Try again later."
                in 300..399 -> "The email service redirected the request. Configure its final API base URL."
                else -> "The email service rejected the message. Check the provider settings and verified sender."
            })
        } catch (failure: EmailDeliveryException) { throw failure }
          catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw EmailDeliveryException("Email delivery was interrupted. Check delivery status before retrying.") }
          catch (_: Exception) { throw EmailDeliveryException("The email service could not be reached within the request timeout. Check delivery status before retrying.") }
    }
}
