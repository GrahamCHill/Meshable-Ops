package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.EmailDeliveryResult
import com.starter.exception.*
import com.starter.model.postgres.PluginSettings
import com.starter.notification.*
import com.starter.email.*
import com.starter.plugins.integrations.*
import com.starter.plugins.telegram.TelegramPlugin
import com.starter.plugins.teams.TeamsPlugin
import com.starter.plugins.whatsapp.WhatsAppPlugin
import com.starter.plugins.sharepoint.SharePointPlugin
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class PluginIntegrationsTest {
    private val settings = mock(PluginSettingsRepository::class.java)
    private val configuration = mock(PluginConfigurationService::class.java)
    private val http = mock(IntegrationHttpClient::class.java)
    private val mapper = jacksonObjectMapper()
    private val registry = PluginRegistry(listOf(TelegramPlugin(), TeamsPlugin(), WhatsAppPlugin(), SharePointPlugin()), settings)
    private val messaging = PluginMessagingService(registry, configuration, http, mapper)
    private fun enable(id: String, values: Map<String, String>) {
        `when`(settings.findById(id)).thenReturn(Optional.of(PluginSettings(id, true, "ADMIN")))
        `when`(configuration.current(id)).thenReturn(values)
        `when`(configuration.view(id)).thenReturn(PluginConfigurationView(emptyMap(),emptySet(),emptyList(),0,true))
    }
    @Test fun `disabled integrations make no provider calls`() {
        `when`(settings.findById("telegram")).thenReturn(Optional.empty())
        assertThat(messaging.available("telegram")).isFalse()
        assertThatThrownBy { messaging.send("telegram", ChannelMessage("hello")) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(http, configuration)
    }
    @Test fun `telegram uses the saved destination and bounded text without parsing markup`() {
        enable("telegram",mapOf("botToken" to "123456:private_token", "chatId" to "-10012345"))
        val calls = mutableListOf<List<Any?>>()
        val transport = mock(IntegrationHttpClient::class.java) { call ->
            if (call.method.name == "request") {calls += call.arguments.toList(); IntegrationResponse(200,mapper.readTree("{\"ok\":true}"))} else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
        }
        val result = PluginMessagingService(registry,configuration,transport,mapper).send("telegram",ChannelMessage("<b>hello</b>","ignored"))
        assertThat(result.status).isEqualTo("ACCEPTED")
        val json=mapper.readTree(calls.single()[3] as String)
        assertThat(json.path("chat_id").asText()).isEqualTo("-10012345")
        assertThat(json.path("text").asText()).isEqualTo("<b>hello</b>")
        assertThat(json.has("parse_mode")).isFalse()
        assertThat(result.toString()).doesNotContain("private_token")
    }
    @Test fun `whatsapp is supplied by a plugin channel and builds approved template messages`() {
        enable("whatsapp", mapOf("accessToken" to "canary-access", "phoneNumberId" to "12345", "apiVersion" to "v25.0", "templateName" to "quote_ready", "languageCode" to "en_US"))
        val calls=mutableListOf<List<Any?>>()
        val transport=mock(IntegrationHttpClient::class.java) { call ->
            if(call.method.name=="request") {calls+=call.arguments.toList(); IntegrationResponse(200,mapper.readTree("{\"messages\":[{\"id\":\"message\"}]}"))} else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
        }
        val adapter=WhatsAppChannelAdapter(PluginMessagingService(registry,configuration,transport,mapper))
        val transportRegistry=com.starter.notification.NotificationTransports(mock(EmailDeliveryService::class.java),mock(EmailSettingsService::class.java),listOf(adapter),registry)
        val policy=mock(com.starter.notification.NotificationSettingsService::class.java)
        `when`(policy.methodEnabled("whatsapp")).thenReturn(true)
        `when`(policy.permitted("general.message","whatsapp")).thenReturn(true)
        val journal=mock(com.starter.notification.NotificationDeliveryRecorder::class.java) {call -> if(call.method.name=="claim") java.util.UUID.randomUUID() else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)}
        val notifications=NotificationService(transportRegistry,policy,mock(com.starter.notification.NotificationCatalogue::class.java),com.starter.notification.NotificationRenderer(),journal,mock(com.starter.service.branding.BrandingPreferencesService::class.java))
        assertThat(notifications.available("whatsapp")).isTrue()
        assertThat(notifications.send("whatsapp",ChannelMessage(recipient="27821234567",templateParameters=listOf("Quote 42"))).status).isEqualTo("ACCEPTED")
        val json=mapper.readTree(calls.single()[3] as String)
        assertThat(json.path("type").asText()).isEqualTo("template")
        assertThat(json.path("template").path("name").asText()).isEqualTo("quote_ready")
        assertThat(json.path("template").path("components")[0].path("parameters")[0].path("text").asText()).isEqualTo("Quote 42")
        assertThatThrownBy { messaging.send("whatsapp",ChannelMessage("arbitrary free text","27821234567")) }.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(http)
    }
    @Test fun `graph lists only the configured root and returns metadata without tokens or download URLs`() {
        enable("sharepoint", mapOf("tenantId" to "12345678-1234-1234-1234-123456789abc", "clientId" to "12345678-1234-1234-1234-123456789abc", "clientSecret" to "private-secret", "driveId" to "drive!1", "rootItemId" to "folder1"))
        val calls=mutableListOf<List<Any?>>()
        val transport=mock(IntegrationHttpClient::class.java) { call ->
            if(call.method.name=="request") {
                calls+=call.arguments.toList()
                if(call.arguments[0]=="POST") IntegrationResponse(200,mapper.readTree("{\"access_token\":\"private-token\"}"))
                else IntegrationResponse(200,mapper.readTree("""{"value":[{"id":"one","name":"Quote.pdf","size":50,"folder":null,"@microsoft.graph.downloadUrl":"secret-url"}],"@odata.nextLink":"https://untrusted/next"}"""))
            } else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
        }
        val result=MicrosoftDriveService(registry,configuration,transport).files("sharepoint")
        assertThat(calls).hasSize(3)
        assertThat(calls[2][1] as String).contains("/items/folder1/children").doesNotContain("/me/")
        assertThat(result.items.single().name).isEqualTo("Quote.pdf")
        assertThat(result.items.single().folder).isFalse()
        assertThat(result.hasMore).isTrue()
        assertThat(mapper.writeValueAsString(result)).doesNotContain("private-token","private-secret","secret-url","untrusted")
    }
    @Test fun `provider failures are optional sanitized results and endpoints reject arbitrary hosts`() {
        enable("teams",mapOf("webhookUrl" to "https://flow.logic.azure.com/workflows/abc?sig=secret"))
        val transport=mock(IntegrationHttpClient::class.java) { call ->
            if(call.method.name=="request") throw IntegrationUnavailable("Provider returned HTTP 429. Check the plugin credentials and permissions.")
            org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
        }
        val result=PluginMessagingService(registry,configuration,transport,mapper).send("teams",ChannelMessage("hello"))
        assertThat(result.status).isEqualTo("UNAVAILABLE")
        assertThat(result.message).contains("429").doesNotContain("sig=secret")
        val client=IntegrationHttpClient(mapper)
        listOf("http://api.telegram.org/test","https://127.0.0.1/test","https://graph.microsoft.com.attacker.test/test","https://user:password@graph.microsoft.com/test").forEach { url ->
            assertThatThrownBy { client.validate(url) }.isInstanceOf(IntegrationUnavailable::class.java)
        }
    }
}
