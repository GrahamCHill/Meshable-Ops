package com.starter.plugins

import com.starter.plugins.calendar.*
import com.starter.plugins.integrations.IntegrationUnavailable
import com.starter.exception.*
import com.starter.model.postgres.User
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.time.*
import java.util.*

class CalendarTest {
    private val events = mock(CalendarEventRepository::class.java)
    private val actors = mock(PluginActor::class.java)
    private val registry = mock(PluginRegistry::class.java)
    private val jdbc = mock(JdbcTemplate::class.java)
    private val service = CalendarService(events,actors,registry,jdbc)
    private val user = User(email="member@example.test")
    private val start = Instant.parse("2026-10-24T09:00:00Z")
    private val input = CalendarEventRequest("Meeting","Notes",start,start.plusSeconds(3600))
    init { `when`(actors.current(user.email)).thenReturn(user) }
    @Test fun `event ownership and stale revisions protect updates and deletion`() {
        val event = CalendarEvent(ownerId=UUID.randomUUID(),revision=2)
        `when`(events.findById(event.id)).thenReturn(Optional.of(event))
        assertThatThrownBy { service.owned(user.email,event.id) }.isInstanceOf(NotFoundException::class.java)
        `when`(actors.current(user.email)).thenReturn(user.copy(role="ADMIN"))
        assertThat(service.owned(user.email,event.id)).isSameAs(event)
        event.personal=true
        assertThatThrownBy {service.owned(user.email,event.id)}.isInstanceOf(NotFoundException::class.java)
        event.personal=false
        assertThatThrownBy { service.save(user.email,event.id,input) }.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy { service.delete(user.email,event.id,1) }.isInstanceOf(ConflictException::class.java)
        verify(events,never()).delete(event)
    }
    @Test fun `invalid duration range title and all day boundaries are rejected`() {
        CalendarService.validateEvent(input)
        listOf(input.copy(end=start),input.copy(title=" "),input.copy(description="a".repeat(4001)),input.copy(end=start.plus(Duration.ofDays(367))),input.copy(allDay=true)).forEach {
            assertThatThrownBy { CalendarService.validateEvent(it) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThatThrownBy { CalendarService.validateWindow(start,start.plus(Duration.ofDays(94))) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `ICS export escapes injection folds UTF8 safely and round trips dates`() {
        val title = "Board, review; "+"révision ".repeat(20)+"\r\nATTENDEE:intruder"
        val row = CalendarEventView("sample",title,"Line one\nLine two \\ end","2026-10-24","2026-10-25",true)
        val result = IcalendarCodec.export(listOf(row))
        assertThat(result).doesNotContain("\r\nATTENDEE:")
        result.split("\r\n").forEach { assertThat(it.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(75) }
        val parsed = IcalendarCodec.parse(result,"calendar-caldav").events.single()
        assertThat(parsed.title).isEqualTo(title.replace("\r\n","\n"))
        assertThat(parsed.description).isEqualTo(row.description)
        assertThat(parsed.start).isEqualTo(row.start)
        assertThat(parsed.end).isEqualTo(row.end)
    }
    @Test fun `CalDAV parses UTC and known TZID events but flags unexpanded recurrence and floating time`() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\nSUMMARY:Meeting\r\nDTSTART;TZID=Africa/Johannesburg:20261024T110000\r\nDTEND;TZID=Africa/Johannesburg:20261024T120000\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        assertThat(IcalendarCodec.parse(ics,"calendar-caldav").events.single().start).isEqualTo(start.toString())
        val recurring = IcalendarCodec.parse(ics.replace("UID:one","UID:one\r\nRRULE:FREQ=DAILY"),"calendar-caldav")
        assertThat(recurring.events).isEmpty(); assertThat(recurring.notice).contains("recurrence")
        val floating = IcalendarCodec.parse(ics.replace(";TZID=Africa/Johannesburg",""),"calendar-caldav")
        assertThat(floating.events).isEmpty(); assertThat(floating.truncated).isTrue()
    }
    @Test fun `CalDAV XML refuses external entity and DTD expansion`() {
        val malicious = """<?xml version="1.0"?><!DOCTYPE data [<!ENTITY file SYSTEM "file:///private">]><data>&file;</data>"""
        assertThatThrownBy { CalDavCalendarProvider.parse(malicious,start,start.plusSeconds(3600)) }.isInstanceOf(IntegrationUnavailable::class.java)
    }
    @Test fun `disabled supporting plugin prevents provider calls and role demotion discards results`() {
        var calls = 0
        val provider = object : CalendarProvider {
            override val pluginId = "calendar-google"
            override fun events(from: Instant,until: Instant): CalendarWindow { calls++; return CalendarWindow(emptyList()) }
            override fun publish(event: CalendarEvent): CalendarPublication { calls++;return CalendarPublication() }
        }
        val connections = CalendarConnections(listOf(provider),actors,registry,service)
        `when`(actors.current(user.email)).thenReturn(user.copy(role="ADMIN"))
        doThrow(NotFoundException("Unavailable")).`when`(registry).requireCapability("calendar","calendar.provider","calendar-google","ADMIN")
        assertThatThrownBy { connections.window(user.email,"calendar-google",start,start.plusSeconds(3600)) }.isInstanceOf(NotFoundException::class.java)
        assertThat(calls).isZero()
        reset(registry)
        `when`(actors.current(user.email)).thenReturn(user.copy(role="ADMIN"),user)
        assertThatThrownBy { connections.window(user.email,"calendar-google",start,start.plusSeconds(3600)) }.isInstanceOf(ForbiddenException::class.java)
        assertThat(calls).isEqualTo(1)
    }
}
