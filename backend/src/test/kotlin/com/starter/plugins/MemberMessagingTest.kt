package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.model.postgres.PluginSettings
import com.starter.model.postgres.User
import com.starter.notification.ChannelMessage
import com.starter.plugins.discord.DiscordPlugin
import com.starter.plugins.integrations.*
import com.starter.plugins.telegram.TelegramPlugin
import com.starter.plugins.teams.TeamsPlugin
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class MemberMessagingTest {
    private val mapper = jacksonObjectMapper()
    private val settings = mock(PluginSettingsRepository::class.java)
    private val config = mock(PluginConfigurationService::class.java)
    private val users = mock(UserRepository::class.java)
    private val registry = PluginRegistry(listOf(DiscordPlugin(), TelegramPlugin(), TeamsPlugin()), settings)
    private val member = User(email = "private@example.test")
    private val calls = mutableListOf<List<Any?>>()
    private var identity: MemberMessageIdentity? = null
    private var chatType = "oneOnOne"
    private val jdbc = mock(JdbcTemplate::class.java) { call ->
        if (call.method.name == "query" && (call.arguments[0] as? String)?.startsWith("SELECT * FROM plugin_") == true) listOfNotNull(identity)
        else Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val http = mock(IntegrationHttpClient::class.java) { call ->
        if (call.method.name == "request") {
            calls += call.arguments.toList()
            val url = call.arguments[1] as String
            val json = when {
                url.endsWith("users/@me/channels") -> """{"id":"12345678901"}"""
                url.endsWith("/members") -> """{"value":[{"userId":"${identity!!.remoteUserId}"}]}"""
                url.contains("graph.microsoft.com") -> """{"chatType":"$chatType"}"""
                else -> """{"ok":true}"""
            }
            IntegrationResponse(200, mapper.readTree(json))
        } else Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val messaging = MemberMessaging(registry, config, users, jdbc, http, mapper)
    private fun enable(id: String, values: Map<String, String>) {
        `when`(settings.findById(id)).thenReturn(Optional.of(PluginSettings(id, true, "ADMIN")))
        `when`(config.current(id)).thenReturn(values)
        `when`(config.view(id)).thenReturn(PluginConfigurationView(emptyMap(), emptySet(), emptyList(), 0, true))
        `when`(users.findById(member.id)).thenReturn(Optional.of(member))
    }
    @Test fun `disabled or unlinked providers never fall back to shared destinations`() {
        enable("telegram", mapOf("botToken" to "canary", "chatId" to "-100000001"))
        assertThat(messaging.sendMember("telegram", ChannelMessage("private", member.id.toString())).status).isEqualTo("UNAVAILABLE")
        verifyNoInteractions(http)
        `when`(settings.findById("telegram")).thenReturn(Optional.empty())
        assertThat(messaging.available("telegram")).isFalse()
    }
    @Test fun `discord resolves a direct conversation and disables remote mentions`() {
        enable("discord", mapOf("botToken" to "canary"))
        identity = MemberMessageIdentity(member.id, "12345678900", "Name", "", 0)
        assertThat(messaging.sendMember("discord", ChannelMessage("@everyone private", member.id.toString())).status).isEqualTo("ACCEPTED")
        assertThat(calls).hasSize(2)
        assertThat(mapper.readTree(calls[0][3] as String).path("recipient_id").asText()).isEqualTo(identity!!.remoteUserId)
        val payload = mapper.readTree(calls[1][3] as String)
        assertThat(payload.path("allowed_mentions").path("parse").size()).isZero()
    }
    @Test fun `teams rejects group chats before sending private content`() {
        enable("teams", mapOf("mode" to "GRAPH", "accessToken" to "canary"))
        identity = MemberMessageIdentity(member.id, member.id.toString(), "Name", "19:private@thread.v2", 0)
        chatType = "group"
        assertThat(messaging.sendMember("teams", ChannelMessage("private", member.id.toString())).status).isEqualTo("UNAVAILABLE")
        assertThat(calls).hasSize(1)
        assertThat(calls.single()[0]).isEqualTo("GET")
    }
    @Test fun `teams verifies mapped identity and sends plain text to the private chat`() {
        enable("teams", mapOf("mode" to "GRAPH", "accessToken" to "canary"))
        identity = MemberMessageIdentity(member.id, member.id.toString(), "Name", "19:private@thread.v2", 0)
        assertThat(messaging.sendMember("teams", ChannelMessage("<b>private</b>", member.id.toString())).status).isEqualTo("ACCEPTED")
        assertThat(calls).hasSize(3)
        assertThat(mapper.readTree(calls.last()[3] as String).path("body").path("contentType").asText()).isEqualTo("text")
    }
    @Test fun `telegram member identities cannot refer to a shared group`() {
        `when`(users.findById(member.id)).thenReturn(Optional.of(member))
        assertThatThrownBy { messaging.save("telegram", member.id, MemberIdentityInput("-100000001")) }.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(http)
    }
}
