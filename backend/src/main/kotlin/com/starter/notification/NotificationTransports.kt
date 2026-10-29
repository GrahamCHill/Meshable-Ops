package com.starter.notification

import com.starter.dto.*
import com.starter.email.*
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class NotificationTransports(private val email: EmailDeliveryService,private val emailSettings: EmailSettingsService,
    private val adapters: List<NotificationChannelAdapter>,private val plugins: PluginRegistry,
    private val workflows: com.starter.plugins.workflows.WorkflowEgressGuard? = null) {
    private val core=NotificationMethod("email","Email",null,NotificationRecipientKind.EMAIL,true,true,true,maxTextLength=20000)
    init {
        require(adapters.map {it.channel}.distinct().size==adapters.size && adapters.none {it.channel=="email"}) {"Duplicate notification method"}
        adapters.forEach {
            require(it.channel.matches(Regex("[a-z][a-z0-9.-]{1,79}")) && it.label.isNotBlank() && it.label.length<=100 && it.maxTextLength in 1..20000)
            require(PluginExtension.NOTIFICATIONS in plugins.definition(it.pluginId).extensions) {"Declare the NOTIFICATIONS extension before registering a delivery method"}
            require(!it.supportsOtp || it.recipientKind!=NotificationRecipientKind.FIXED) {"Shared destinations cannot receive OTPs"}
        }
    }
    fun all()=listOf(core)+adapters.map {NotificationMethod(it.channel,it.label,it.pluginId,it.recipientKind,it.supportsOtp,it.supportsHtml,it.supportsAttachments,it.parameterized,it.maxTextLength)}
    fun visible() = all().filter { method -> method.pluginId == null || runCatching { plugins.requireAvailable(method.pluginId,"ADMIN"); adapters.find { it.channel == method.id }?.let { !it.sms || plugins.definition(it.pluginId).providesSms } == true }.getOrDefault(false) }
    fun get(id: String)=all().find {it.id==id} ?: throw NotFoundException("Delivery method unavailable")
    fun reachable(id: String, userId: java.util.UUID) = get(id).recipientKind != NotificationRecipientKind.MEMBER || adapters.find { it.channel == id }?.reachable(userId) == true
    fun available(id: String)=runCatching {
        if(id=="email") emailSettings.current().let {it.enabled && runCatching {validateEmailConfiguration(it)}.isSuccess}
        else adapters.find {it.channel==id}?.let {plugins.requireAvailable(it.pluginId,"ADMIN");(!it.sms || plugins.definition(it.pluginId).providesSms) && it.available()} ?: false
    }.getOrDefault(false)
    fun send(id: String,message: ChannelMessage): EmailDeliveryResult {
        validate(id,message)
        if(!available(id)) return EmailDeliveryResult("UNAVAILABLE","The delivery method is disabled or incomplete")
        workflows?.beforeSend()
        val safe=if(message.html.isBlank()) message else message.copy(html=NotificationRenderer().cleanHtml(message.html))
        return if(id=="email") email.send(SendEmailRequest(safe.recipient,safe.subject,safe.text,safe.html),safe.attachments)
        else adapters.first {it.channel==id}.let {plugins.requireAvailable(it.pluginId,"ADMIN");it.send(safe)}
    }
    fun validate(id: String,message: ChannelMessage) {
        val method=get(id)
        if(message.text.length>method.maxTextLength || message.html.length>100000 || message.subject.length>200 || message.subject.any {it.isISOControl()} || message.recipient.length>254 || message.text.any {it.isISOControl() && it !in "\r\n\t"}) throw BadRequestException("Message exceeds the delivery method's limits")
        if(message.templateParameters.size>20 || message.templateParameters.any {it.length>1000 || it.any {c -> c.isISOControl() && c !in "\r\n\t"}} || message.providerTemplate.length>200) throw BadRequestException("Provider template parameters exceed their limits")
        if(message.html.isNotBlank() && !method.supportsHtml || message.attachments.isNotEmpty() && !method.supportsAttachments) throw BadRequestException("This delivery method cannot send that content")
        if(message.attachments.size>1 || message.attachments.any {it.bytes.isEmpty() || it.bytes.size>EmailAttachment.MAX_BYTES || !it.filename.endsWith(".pdf",true) || it.filename.length>200 || it.filename.any {c -> c.isISOControl() || c in "\\\"/"}}) throw BadRequestException("Attach one PDF within the configured attachment limit")
        when(method.recipientKind) {
            NotificationRecipientKind.EMAIL -> validateMailbox(message.recipient,"recipient")
            NotificationRecipientKind.PHONE -> if(!message.recipient.matches(Regex("\\+?[1-9][0-9]{6,14}"))) throw BadRequestException("Use an international phone number")
            NotificationRecipientKind.FIXED -> Unit
            NotificationRecipientKind.MEMBER -> if (runCatching { java.util.UUID.fromString(message.recipient) }.isFailure) throw BadRequestException("Choose a linked internal member")
        }
    }
}
