package com.starter.notification

import com.starter.plugins.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class NotificationVisibilityTest {
    @Test fun `disabled plugin methods disappear while saved rules survive edits and reenabling`() {
        val f=NotificationFixture();f.configure();f.pluginEnabled=false
        val view=f.settings.view()
        assertThat(view.methods.map {it.method.id}).containsExactly("email")
        val general=view.notifications.first {it.definition.id=="general.message"}
        assertThat(general.eligibleMethods).containsExactly("email")
        f.settings.save(NotificationSettingsRequest(view.revision,mapOf("email" to true),mapOf("general.message" to general.rule)))
        assertThat(f.settings.rule(f.catalogue.get("general.message")).defaultMethods).contains(f.sms.channel)
        f.pluginEnabled=true
        assertThat(f.settings.view().methods.map {it.method.id}).contains(f.sms.channel)
    }
    @Test fun `SMS delivery requires the plugin SMS flag even when its connection is enabled`() {
        val f=NotificationFixture();f.configure()
        `when`(f.plugins.definition("smsportal")).thenReturn(PluginManifest("smsportal","SMSportal","1.0.0","",extensions=setOf(PluginExtension.NOTIFICATIONS),providesSms=false))
        assertThat(f.transports.available(f.sms.channel)).isFalse()
        assertThat(f.transports.visible().map {it.id}).doesNotContain(f.sms.channel)
        assertThat(f.sms.sent).isEmpty()
    }
}
