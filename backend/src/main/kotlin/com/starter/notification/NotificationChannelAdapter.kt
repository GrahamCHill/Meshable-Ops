package com.starter.notification

import com.starter.dto.EmailDeliveryResult

/** Plugins register channels without expanding the core channel enum. */
interface NotificationChannelAdapter {
    val channel: String
    fun available(): Boolean
    fun send(message: ChannelMessage): EmailDeliveryResult
}
data class ChannelMessage(val text: String = "", val recipient: String = "", val templateParameters: List<String> = emptyList()) {
    override fun toString() = "ChannelMessage()"
}
