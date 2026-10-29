package com.starter.notification

import com.starter.dto.*
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.service.branding.BrandingPreferencesService
import org.springframework.stereotype.Service
import java.util.UUID

enum class NotificationChannel { EMAIL, SMS }

/** Shared, server-side delivery entry point for core features and reviewed plugins. */
@Service
class NotificationService(private val transports: NotificationTransports,private val settings: NotificationSettingsService,
    private val catalogue: NotificationCatalogue,private val renderer: NotificationRenderer,private val journal: NotificationDeliveryRecorder,
    private val branding: BrandingPreferencesService, private val workflows: com.starter.plugins.workflows.WorkflowEgressGuard? = null) {
    fun available(channel: String)=settings.methodEnabled(channel) && transports.available(channel)
    fun available(channel: NotificationChannel)=available(channel.name.lowercase())
    fun availableFor(event: String,method: String)=settings.permitted(event,method) && transports.available(method)
    fun availableOtp(method: String="email")=availableFor("security.otp",method) && transports.get(method).supportsOtp
    fun send(channel: NotificationChannel,message: SendEmailRequest,attachments: List<EmailAttachment> = emptyList())=
        if(channel==NotificationChannel.EMAIL) sendEmail("general.message",message,attachments) else EmailDeliveryResult("UNAVAILABLE","Select an installed SMS delivery method")
    fun send(channel: String,message: ChannelMessage): EmailDeliveryResult {
        if(!availableFor("general.message",channel)) return EmailDeliveryResult("DISABLED","This delivery method is disabled for general messages")
        return transmit("general.message",channel,NotificationTarget(),message,UUID.randomUUID().toString())
    }
    fun sendEmail(event: String,message: SendEmailRequest,attachments: List<EmailAttachment> = emptyList()): EmailDeliveryResult =
        deliver(event,"email",NotificationTarget(email=message.recipient),mapOf("subject" to message.subject,"message" to message.text),attachments=attachments,htmlOverride=message.html.takeIf {it.isNotBlank()})
    fun sendOtp(method: String,target: NotificationTarget,code: String,expiresMinutes: Int=5): EmailDeliveryResult =
        deliver("security.otp",method,target,mapOf("code" to code,"expiresMinutes" to expiresMinutes.toString(),"subject" to "Your verification code","message" to ""))
    fun deliver(event: String,methodId: String,target: NotificationTarget,values: Map<String,String>,key: String=UUID.randomUUID().toString(),
        attachments: List<EmailAttachment> = emptyList(),htmlOverride: String?=null,splitLongText: Boolean=false): EmailDeliveryResult {
        if(!availableFor(event,methodId)) return EmailDeliveryResult("DISABLED","This notification or delivery method is disabled or unavailable")
        val definition=catalogue.get(event);val method=transports.get(methodId)
        if(definition.category==NotificationCategory.SECURITY && (!method.supportsOtp || methodId !in target.verifiedMethods || method.recipientKind==NotificationRecipientKind.FIXED))
            throw BadRequestException("Verification codes require a verified direct recipient and an OTP-capable delivery method")
        val recipient=when(method.recipientKind) {NotificationRecipientKind.EMAIL -> target.email;NotificationRecipientKind.PHONE -> target.phone;NotificationRecipientKind.FIXED -> "";NotificationRecipientKind.MEMBER -> target.userId?.toString() ?: throw BadRequestException("Choose an internal member for this delivery method")}
        val rule=settings.rule(definition);val configured=settings.template(definition,method,rule);val template=htmlOverride?.let {configured.copy(html=it)} ?: configured
        val rendered=renderer.render(definition,method,template,mapOf("applicationName" to branding.current().applicationName,"recipientName" to target.name.ifBlank {"Member"},"subject" to definition.label,"message" to "")+values)
        val message=ChannelMessage(if(method.parameterized) "" else rendered.text,recipient,if(method.parameterized) rendered.parameters else emptyList(),rendered.subject,rendered.html,template.providerTemplate,attachments)
        if (splitLongText && !method.parameterized && message.text.length > method.maxTextLength) {
            val parts = splitText(message.text, method.maxTextLength)
            val results = parts.mapIndexed { index, text -> transmit(event,methodId,target,message.copy(text=text),"$key:part:$index") }
            return results.firstOrNull { it.status !in setOf("ACCEPTED","SKIPPED") } ?: EmailDeliveryResult("ACCEPTED","Accepted by the provider in ${parts.size} messages")
        }
        return transmit(event,methodId,target,message,key)
    }
    companion object {
        internal fun splitText(text: String, limit: Int): List<String> {
            require(limit > 1)
            val parts = mutableListOf<String>(); var offset = 0
            while (offset < text.length) {
                var end = (offset + limit).coerceAtMost(text.length)
                if (end < text.length && text[end - 1].isHighSurrogate()) end--
                parts += text.substring(offset,end); offset = end
            }
            return parts
        }
    }
    private fun transmit(event: String,method: String,target: NotificationTarget,message: ChannelMessage,key: String): EmailDeliveryResult {
        transports.validate(method,message)
        if (event != "security.otp") workflows?.beforeSend()
        val attempt=journal.claim(event,method,target,message.recipient,key) ?: return EmailDeliveryResult("SKIPPED","This notification delivery was already attempted")
        val result=try {
            if(!availableFor(event,method)) EmailDeliveryResult("DISABLED","Delivery settings changed") else if (event=="security.otp")
                com.starter.plugins.workflows.WorkflowEgressGuard.securityCode { transports.send(method,message) } else transports.send(method,message)
        } catch (_:Exception) {EmailDeliveryResult("UNAVAILABLE","The delivery method could not confirm acceptance. Check its provider before retrying.")}
        val status=when(result.status) {"ACCEPTED" -> "ACCEPTED";"DISABLED" -> "DISABLED";else -> "FAILED"}
        journal.finish(attempt,status)
        return EmailDeliveryResult(result.status,if(status=="ACCEPTED") "Accepted by the provider. Recipient delivery is not confirmed." else "Delivery was not confirmed. Check the method's configuration and provider before retrying.")
    }
}
