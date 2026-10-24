package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.calendar.*
import com.starter.plugins.integrations.*
import com.starter.plugins.onedrive.OneDrivePlugin
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.exception.NotFoundException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.Optional

class CalendarProvidersTest {
    private val mapper = jacksonObjectMapper()
    private val settings = mock(PluginSettingsRepository::class.java)
    private val configuration = mock(PluginConfigurationService::class.java)
    private val registry = PluginRegistry(listOf(CalendarPlugin(),MicrosoftCalendarPlugin(),GoogleCalendarPlugin(),CalDavCalendarPlugin(),OneDrivePlugin()),settings)
    private val rows = mutableListOf(PluginSettings("calendar",true,"ADMIN"))
    private val calls = mutableListOf<List<Any?>>()
    private var body = "{}"
    private var status = 200
    private val http = mock(IntegrationHttpClient::class.java) { call ->
        if (call.method.name in listOf("request","scoped")) {
            calls += call.arguments.toList()
            if ((call.arguments[1] as String).contains("/token")) IntegrationResponse(200,mapper.readTree("""{"access_token":"private-token"}"""))
            else IntegrationResponse(status,runCatching { mapper.readTree(body) }.getOrNull(),body)
        } else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val start = Instant.parse("2026-10-24T00:00:00Z")
    private val until = Instant.parse("2026-10-25T00:00:00Z")
    init {
        `when`(settings.findAll()).thenAnswer { rows }
        `when`(settings.findById(anyString())).thenAnswer { call -> Optional.ofNullable(rows.find { it.pluginId == call.arguments[0] }) }
    }
    private fun enable(id: String,values: Map<String,String>) {
        rows += PluginSettings(id,true,"ADMIN")
        `when`(configuration.current(id)).thenReturn(values)
    }
    @Test fun `Google refresh grant and expanded bounded events do not return credentials or next links`() {
        enable("calendar-google",mapOf("clientId" to "client","clientSecret" to "secret","refreshToken" to "refresh","calendarId" to "primary"))
        body = """{"items":[{"id":"event1","summary":"Meeting","start":{"dateTime":"2026-10-24T10:00:00+02:00"},"end":{"dateTime":"2026-10-24T11:00:00+02:00"}}],"nextPageToken":"private-page"}"""
        val result = GoogleCalendarProvider(registry,configuration,http,mapper).events(start,until)
        assertThat(result.events.single().start).isEqualTo("2026-10-24T08:00:00Z")
        assertThat(result.truncated).isTrue()
        assertThat(calls[0][3] as String).contains("refresh_token=refresh","grant_type=refresh_token")
        assertThat(calls[1][1] as String).contains("singleEvents=true","maxResults=100")
        assertThat(mapper.writeValueAsString(result)).doesNotContain("private-token","private-page","secret")
    }
    @Test fun `Google publishing uses stable allowed event IDs and leaves existing remote copies intact`() {
        enable("calendar-google",mapOf("clientId" to "client","clientSecret" to "secret","refreshToken" to "refresh","calendarId" to "user@example.test"))
        val provider = GoogleCalendarProvider(registry,configuration,http,mapper)
        val event = CalendarEvent(title="Meeting",startsAt=start,endsAt=until,allDay=true)
        body="{}";status=409
        assertThat(provider.publish(event).message).contains("already present")
        val first = mapper.readTree(calls.last()[4] as String)
        assertThat(first.path("id").asText()).matches("[a-v0-9]{5,1024}")
        assertThat(first.path("start").path("date").asText()).isEqualTo("2026-10-24")
        provider.publish(event)
        assertThat(mapper.readTree(calls.last()[4] as String).path("id")).isEqualTo(first.path("id"))
        assertThat(first.has("attendees")).isFalse()
        IntegrationHttpClient.endpoint(calls.last()[1] as String) // encoded calendar IDs are valid configured endpoint paths.
    }
    @Test fun `Microsoft uses mailbox calendar view and optional credentials from an enabled provider`() {
        enable("calendar-microsoft",mapOf("credentialPlugin" to "onedrive","mailbox" to "owner@example.test"))
        val provider = MicrosoftCalendarProvider(registry,configuration,http,mapper)
        assertThatThrownBy { provider.events(start,until) }.isInstanceOf(NotFoundException::class.java)
        assertThat(calls).isEmpty()
        val guid = "12345678-1234-1234-1234-123456789abc"
        enable("onedrive",mapOf("tenantId" to guid,"clientId" to guid,"clientSecret" to "secret"))
        body = """{"value":[{"id":"one","subject":"Meeting","start":{"dateTime":"2026-10-24T08:00:00","timeZone":"UTC"},"end":{"dateTime":"2026-10-24T09:00:00","timeZone":"UTC"}}]}"""
        assertThat(provider.events(start,until).events.single().start).isEqualTo("2026-10-24T08:00:00Z")
        assertThat(calls.last()[1] as String).contains("/users/owner%40example.test/calendarView?").doesNotContain("/me/")
        rows.last().enabled=false
        calls.clear()
        assertThatThrownBy { provider.events(start,until) }.isInstanceOf(NotFoundException::class.java)
        assertThat(calls).isEmpty()
    }
    @Test fun `Microsoft publication includes transaction identity and no attendees`() {
        val guid = "12345678-1234-1234-1234-123456789abc"
        enable("calendar-microsoft",mapOf("tenantId" to guid,"clientId" to guid,"clientSecret" to "secret","mailbox" to "owner@example.test"))
        val event = CalendarEvent(title="Meeting",startsAt=start,endsAt=until)
        MicrosoftCalendarProvider(registry,configuration,http,mapper).publish(event)
        val payload = mapper.readTree(calls.last()[3] as String)
        assertThat(payload.path("transactionId").asText()).isEqualTo(event.id.toString())
        assertThat(payload.has("attendees")).isFalse()
        assertThat(payload.path("body").path("contentType").asText()).isEqualTo("text")
    }
    @Test fun `CalDAV uses bounded expansion and conditional publication without following response URLs`() {
        enable("calendar-caldav",mapOf("calendarUrl" to "https://calendar.example.test/selected/","username" to "user","password" to "private-password"))
        val event = CalendarEvent(title="Meeting",startsAt=start,endsAt=until,allDay=true)
        val ics = IcalendarCodec.export(listOf(CalendarService.view(event)))
        body = """<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:response><d:href>https://untrusted.test/</d:href><d:propstat><d:prop><c:calendar-data><![CDATA[$ics]]></c:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
        val provider = CalDavCalendarProvider(registry,configuration,http)
        assertThat(provider.events(start,until).events.single().title).isEqualTo("Meeting")
        assertThat(calls.single()[0]).isEqualTo("REPORT")
        assertThat(calls.single()[4] as String).contains("c:expand","20261024T000000Z")
        status=412
        assertThat(provider.publish(event).message).contains("already present")
        assertThat(calls.last()[2]).isEqualTo("${event.id}.ics")
        assertThat((calls.last()[3] as Map<*,*>)["If-None-Match"]).isEqualTo("*")
    }
}
