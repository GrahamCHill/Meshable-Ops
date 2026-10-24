package com.starter.notification

import com.starter.dto.EmailDeliveryResult
import com.starter.email.*
import com.starter.plugins.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class NotificationAdapterContractTest {
    @Test fun `a plugin must declare the notification extension and cannot register duplicate or shared OTP methods`() {
        val registry=mock(PluginRegistry::class.java)
        `when`(registry.definition("smsportal")).thenReturn(PluginManifest("smsportal","SMS","1.0.0",""))
        val email=mock(EmailDeliveryService::class.java);val settings=mock(EmailSettingsService::class.java)
        assertThatThrownBy {NotificationTransports(email,settings,listOf(TestSmsMethod()),registry)}.isInstanceOf(IllegalArgumentException::class.java)
        `when`(registry.definition("smsportal")).thenReturn(PluginManifest("smsportal","SMS","1.0.0","",extensions=setOf(PluginExtension.NOTIFICATIONS)))
        assertThatThrownBy {NotificationTransports(email,settings,listOf(TestSmsMethod(),TestSmsMethod()),registry)}.isInstanceOf(IllegalArgumentException::class.java)
        val shared=object:NotificationChannelAdapter {
            override val channel="smsportal.shared";override val pluginId="smsportal";override val supportsOtp=true
            override fun available()=true
            override fun send(message: ChannelMessage)=EmailDeliveryResult("ACCEPTED","Accepted")
        }
        assertThatThrownBy {NotificationTransports(email,settings,listOf(shared),registry)}.isInstanceOf(IllegalArgumentException::class.java)
    }
    @Test fun `plugins can add named notification types only inside their own namespace`() {
        val f=NotificationFixture()
        fun provider(id: String)=object:NotificationDefinitionProvider {
            override val pluginId="smsportal"
            override fun definitions()=listOf(NotificationDefinition(id,"Delivery update","",NotificationCategory.GENERAL,setOf("message"),"Update","{{message}}"))
        }
        assertThat(NotificationCatalogue(listOf(provider("smsportal.delivery-update")),f.plugins).get("smsportal.delivery-update").pluginId).isEqualTo("smsportal")
        assertThatThrownBy {NotificationCatalogue(listOf(provider("security.otp")),f.plugins)}.isInstanceOf(IllegalArgumentException::class.java)
    }
}
