package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.calendar.*
import com.starter.plugins.integrations.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant

class PersonalCalendarTransportTest {
    private val mapper=jacksonObjectMapper()
    private val calls=mutableListOf<List<Any?>>()
    private var payload="{}"
    private var status=200
    private val http=mock(IntegrationHttpClient::class.java) { call ->
        if (call.method.name in setOf("request","scoped")) {
            calls+=call.arguments.toList()
            if ((call.arguments[1] as String).contains("/token")) IntegrationResponse(200,mapper.readTree("""{"access_token":"private-token","refresh_token":"rotated-token"}"""))
            else IntegrationResponse(status,runCatching {mapper.readTree(payload)}.getOrNull(),payload,mapOf("ETag" to "\"v1\""))
        } else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val start=Instant.parse("2026-10-24T09:00:00Z")
    private val event=CalendarEvent(title="Appointment",startsAt=start,endsAt=start.plusSeconds(3600))
    @Test fun `personal Microsoft exchanges a delegated grant and uses me rather than a configured mailbox`() {
        val values=mapOf("tenantId" to "common","clientId" to "12345678-1234-1234-1234-123456789abc","refreshToken" to "private-grant")
        val remote=PersonalCalendarRemoteFactory(http,mapper).open("calendar-microsoft",values)
        assertThat(remote.credentials.getValue("refreshToken")).isEqualTo("rotated-token")
        assertThat(calls.single()[3] as String).contains("grant_type=refresh_token","offline_access","Calendars.ReadWrite").doesNotContain("client_credentials")
        payload="""{"value":[]}"""
        remote.list(start,start.plusSeconds(3600))
        assertThat(calls.last()[1]).isEqualTo("https://graph.microsoft.com/v1.0/me/")
    }
    @Test fun `conditional JSON updates preserve revision guards and clear the unused date shape`() {
        val remote=PersonalJsonCalendarRemote(http,mapper,"calendar-google",mapOf("calendarId" to "primary"),"token")
        payload="""{"id":"one","etag":"v2","summary":"Appointment","start":{"dateTime":"2026-10-24T09:00:00Z"},"end":{"dateTime":"2026-10-24T10:00:00Z"}}"""
        remote.update("one","v1",event)
        val call=calls.last()
        assertThat(call[0]).isEqualTo("PATCH");assertThat((call[3] as Map<*,*>)["If-Match"]).isEqualTo("v1")
        assertThat(mapper.readTree(call[4] as String).path("start").path("date").isNull).isTrue()
        status=412
        assertThatThrownBy {remote.update("one","v1",event)}.isInstanceOf(RemoteCalendarConflict::class.java)
        assertThatThrownBy {remote.delete("one","v1")}.isInstanceOf(RemoteCalendarConflict::class.java)
    }
    @Test fun `CalDAV preserves an existing external UID when updating and fences resource targets`() {
        val remote=PersonalCalDavRemote(http,mapOf("calendarUrl" to "https://calendar.example.test/me/","username" to "user","password" to "pass"))
        payload=IcalendarCodec.export(listOf(CalendarService.view(event)),mapOf(event.id.toString() to "external-uid@example.test")).replace("END:VEVENT","LOCATION:Office\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nDESCRIPTION:Reminder\r\nEND:VALARM\r\nEND:VEVENT")
        remote.get("resource.ics")
        remote.update("resource.ics","\"v1\"",event)
        val write=calls.single { it[0]=="PUT" }
        assertThat(write[4] as String).contains("UID:external-uid@example.test").doesNotContain("UID:${event.id}@weaveops")
        assertThat(write[4] as String).contains("LOCATION:Office","BEGIN:VALARM","DESCRIPTION:Reminder","SEQUENCE:1")
        listOf("https://attacker.test/file.ics","/outside/file.ics","../file.ics","https://user:secret@calendar.example.test/me/file.ics","file.ics?token=secret").forEach { href ->
            assertThatThrownBy {PersonalCalDavRemote.resourceId("https://calendar.example.test/me/",href)}.isInstanceOf(RuntimeException::class.java)
        }
        assertThat(PersonalCalDavRemote.resourceId("https://calendar.example.test/me/","/me/resource.ics")).isEqualTo("resource.ics")
    }
    @Test fun `request budgets stop unbounded sync traffic and recurrence or invitations are flagged`() {
        val remote=PersonalJsonCalendarRemote(http,mapper,"calendar-google",mapOf("calendarId" to "primary"),"token")
        payload="""{"items":[{"id":"series","recurringEventId":"parent"},{"id":"meeting","attendees":[{"email":"guest@example.test"}]}]}"""
        val window=remote.list(start,start.plusSeconds(3600))
        assertThat(window.events).isEmpty();assertThat(window.notice).contains("recurring/invited")
        repeat(19) {remote.list(start,start.plusSeconds(3600))}
        assertThatThrownBy {remote.list(start,start.plusSeconds(3600))}.isInstanceOf(IntegrationUnavailable::class.java)
        assertThat(calls).hasSize(20)
    }
}
