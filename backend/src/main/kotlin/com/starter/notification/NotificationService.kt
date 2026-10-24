package com.starter.notification

import com.starter.dto.EmailDeliveryResult
import com.starter.dto.SendEmailRequest
import com.starter.email.EmailDeliveryService
import com.starter.email.EmailAttachment
import com.starter.email.EmailSettingsService
import com.starter.email.validateEmailConfiguration
import org.springframework.stereotype.Service

enum class NotificationChannel { EMAIL, SMS }

/** Core mail stays stable; optional channels are supplied by installed plugins. */
@Service
class NotificationService(private val email: EmailDeliveryService, private val settings: EmailSettingsService,
    private val adapters: List<NotificationChannelAdapter> = emptyList()) {
    init { require(adapters.map { it.channel }.distinct().size == adapters.size) { "Duplicate notification channel" } }
    fun available(channel: String) = adapters.find { it.channel == channel }?.available() ?: false
    fun send(channel: String, message: ChannelMessage): EmailDeliveryResult = adapters.find { it.channel == channel }?.send(message)
        ?: EmailDeliveryResult("UNAVAILABLE", "This notification plugin is not configured")
    fun available(channel: NotificationChannel): Boolean = channel == NotificationChannel.EMAIL && runCatching {
        settings.current().let { it.enabled && runCatching { validateEmailConfiguration(it) }.isSuccess }
    }.getOrDefault(false)
    fun send(channel: NotificationChannel, message: SendEmailRequest, attachments: List<EmailAttachment> = emptyList()): EmailDeliveryResult =
        if (channel == NotificationChannel.EMAIL) email.send(message, attachments) else EmailDeliveryResult("UNAVAILABLE", "This notification channel is not configured")
}
