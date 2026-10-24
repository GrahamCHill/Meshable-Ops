package com.starter.plugins.integrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.EmailDeliveryResult
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.notification.ChannelMessage
import com.starter.notification.NotificationChannelAdapter
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.PluginRegistry
import org.springframework.stereotype.Service
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

@Service
class PluginMessagingService(private val registry: PluginRegistry, private val settings: PluginConfigurationService,
    private val http: IntegrationHttpClient, private val mapper: ObjectMapper) {
    private val workers = Semaphore(2)
    private val ids = setOf("telegram", "teams", "whatsapp")
    fun available(id: String): Boolean = id in ids && runCatching {
        registry.requireAvailable(id, "ADMIN")
        settings.view(id).missingRequired.isEmpty()
    }.getOrDefault(false)
    fun send(id: String, message: ChannelMessage): EmailDeliveryResult {
        if (id !in ids) throw NotFoundException("Messaging plugin unavailable")
        registry.requireAvailable(id, "ADMIN")
        if (!available(id)) return EmailDeliveryResult("UNAVAILABLE", "Complete this plugin's configuration first")
        if (message.text.length > 4000 || message.recipient.length > 40 || message.templateParameters.size > 20 || message.providerTemplate.isNotBlank() && !message.providerTemplate.matches(Regex("[a-z0-9_]{1,200}")) ||
            message.templateParameters.any { it.length > 1000 } || message.text.any { it.isISOControl() && it !in "\n\r\t" })
            throw BadRequestException("Use a message up to 4000 characters and at most 20 template parameters")
        if (!workers.tryAcquire()) return EmailDeliveryResult("UNAVAILABLE", "Plugin delivery is busy. Try again shortly.")
        try {
            val config = settings.current(id)
            if (registry.definition(id).settingsFields.any { it.required && config[it.key].isNullOrBlank() })
                return EmailDeliveryResult("UNAVAILABLE", "Plugin configuration changed. Complete its connection settings first.")
            registry.validateConfiguration(id, config)
            registry.requireAvailable(id, "ADMIN")
            val response = when (id) {
                "telegram" -> {
                    if (message.text.isBlank()) throw BadRequestException("Enter a message")
                    http.request("POST", "https://api.telegram.org/bot${config.getValue("botToken")}/sendMessage",
                        body = mapper.writeValueAsString(mapOf("chat_id" to config.getValue("chatId"), "text" to message.text)))
                }
                "teams" -> {
                    if (message.text.isBlank()) throw BadRequestException("Enter a message")
                    val card = mapOf("type" to "message", "attachments" to listOf(mapOf("contentType" to "application/vnd.microsoft.card.adaptive",
                        "content" to mapOf("\$schema" to "https://adaptivecards.io/schemas/adaptive-card.json", "type" to "AdaptiveCard", "version" to "1.2",
                            "body" to listOf(mapOf("type" to "TextBlock", "text" to message.text, "wrap" to true))))))
                    http.request("POST", config.getValue("webhookUrl"), body = mapper.writeValueAsString(card))
                }
                else -> {
                    if (!message.recipient.matches(Regex("[1-9][0-9]{6,14}")) || message.text.isNotBlank()) throw BadRequestException("Use an international phone number without + and approved template parameters; free-text messaging is not supported")
                    val template = mutableMapOf<String, Any>("name" to message.providerTemplate.ifBlank {config.getValue("templateName")}, "language" to mapOf("code" to config.getValue("languageCode")))
                    if (message.templateParameters.isNotEmpty()) template["components"] = listOf(mapOf("type" to "body", "parameters" to message.templateParameters.map { mapOf("type" to "text", "text" to it) }))
                    http.request("POST", "https://graph.facebook.com/${config.getValue("apiVersion")}/${config.getValue("phoneNumberId")}/messages",
                        headers = mapOf("Authorization" to "Bearer ${config.getValue("accessToken")}"), body = mapper.writeValueAsString(mapOf("messaging_product" to "whatsapp", "to" to message.recipient, "type" to "template", "template" to template)))
                }
            }
            if (id == "telegram" && response.body?.path("ok")?.asBoolean() != true) throw IntegrationUnavailable("Telegram did not accept the message")
            return EmailDeliveryResult("ACCEPTED", "Accepted by the provider. Delivery to the recipient is not confirmed.")
        } catch (failure: IntegrationUnavailable) { return EmailDeliveryResult("UNAVAILABLE", failure.message ?: "Provider unavailable") }
          finally { workers.release() }
    }
}

abstract class MessagingChannelAdapter(override val channel: String, private val messaging: PluginMessagingService) : NotificationChannelAdapter {
    override fun available() = messaging.available(channel)
    override fun send(message: ChannelMessage): EmailDeliveryResult {
        if (!available()) return EmailDeliveryResult("DISABLED", "This notification plugin is disabled or incomplete")
        return try { messaging.send(channel, message) }
            catch (_: NotFoundException) { EmailDeliveryResult("DISABLED", "This notification plugin was disabled") }
    }
}
abstract class MemberChannelAdapter(override val channel: String, private val members: MemberMessaging) : NotificationChannelAdapter {
    override val recipientKind = com.starter.notification.NotificationRecipientKind.MEMBER
    override fun available() = members.available(channel)
    override fun reachable(userId: java.util.UUID) = members.reachable(channel, userId)
    override fun send(message: ChannelMessage) = members.sendMember(channel, message)
}
@Component class TelegramChannelAdapter(members: MemberMessaging) : MemberChannelAdapter("telegram", members) {override val label="Telegram"}
@Component class TeamsChannelAdapter(members: MemberMessaging) : MemberChannelAdapter("teams", members) {override val label="Microsoft Teams"}
@Component class DiscordChannelAdapter(members: MemberMessaging) : MemberChannelAdapter("discord", members) {override val label="Discord"; override val maxTextLength = 2000}
@Component class WhatsAppChannelAdapter(messaging: PluginMessagingService) : MessagingChannelAdapter("whatsapp", messaging) {
    override val label="WhatsApp Business"
    override val recipientKind=com.starter.notification.NotificationRecipientKind.PHONE
    override val parameterized=true
    override fun send(message: ChannelMessage)=super.send(message.copy(recipient=message.recipient.removePrefix("+")))
}
