package com.starter.notification

import com.starter.dto.EmailDeliveryResult

/** Plugins register channels without expanding the core channel enum. */
interface NotificationChannelAdapter {
    val channel: String
    val pluginId: String get() = channel
    val label: String get() = channel
    val recipientKind: NotificationRecipientKind get() = NotificationRecipientKind.FIXED
    val supportsOtp: Boolean get() = false
    val supportsHtml: Boolean get() = false
    val supportsAttachments: Boolean get() = false
    val parameterized: Boolean get() = false
    val maxTextLength: Int get() = 4000
    fun available(): Boolean
    fun send(message: ChannelMessage): EmailDeliveryResult
}
data class ChannelMessage(val text: String = "", val recipient: String = "", val templateParameters: List<String> = emptyList(),
    val subject: String = "", val html: String = "", val providerTemplate: String = "",
    @get:com.fasterxml.jackson.annotation.JsonIgnore val attachments: List<com.starter.email.EmailAttachment> = emptyList()) {
    override fun toString() = "ChannelMessage()"
}
