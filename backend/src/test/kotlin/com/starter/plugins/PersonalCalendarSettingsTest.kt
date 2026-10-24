package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.calendar.*
import com.starter.security.testEncryption
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class PersonalCalendarSettingsTest {
    private val user=User(email="member@example.test")
    private val actors=mock(PluginActor::class.java)
    private val registry=mock(PluginRegistry::class.java)
    private var stored: PersonalCalendarConnection?=null
    private val connections=mock(PersonalCalendarRepository::class.java) { call -> when(call.method.name) {
        "findByOwnerId" -> stored?.takeIf { it.ownerId==call.arguments[0] }
        "locked" -> Optional.ofNullable(stored?.takeIf { it.id==call.arguments[0] })
        "saveAndFlush" -> (call.arguments[0] as PersonalCalendarConnection).also {stored=it}
        else -> org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    } }
    private val links=mock(CalendarSyncLinkRepository::class.java)
    private val events=mock(CalendarEventRepository::class.java)
    private val service=PersonalCalendarSettingsService(connections,links,events,actors,registry,jacksonObjectMapper(),testEncryption(),mock(JdbcTemplate::class.java))
    private val fields=mapOf("calendarUrl" to "https://calendar.example.test/me/","username" to "private-user","password" to "private-app-password")
    init {
        `when`(actors.current(user.email)).thenReturn(user)
        val calendar=CalendarPlugin().manifest
        val view=PluginView(calendar,true,setOf("ADMIN","USER"),tableAccess=calendar.tables.associate { it.name to PluginTableAccess(it.readRoles,it.writeRoles) })
        `when`(registry.available("USER")).thenReturn(listOf(view,PluginView(CalDavCalendarPlugin().manifest,true,setOf("USER","ADMIN"))))
    }
    @Test fun `personal credentials are write only blank values retain them and other users cannot read the connection`() {
        val view=service.save(user.email,PersonalCalendarRequest("calendar-caldav",fields))
        assertThat(view.configuredSecrets).containsExactlyInAnyOrder("username","password")
        assertThat(jacksonObjectMapper().writeValueAsString(view)).doesNotContain("private-user","private-app-password")
        service.save(user.email,PersonalCalendarRequest("calendar-caldav",mapOf("password" to ""),revision=view.revision))
        assertThat(service.values(stored).getValue("password")).isEqualTo("private-app-password")
        val other=User(email="other@example.test")
        `when`(actors.current(other.email)).thenReturn(other)
        assertThat(service.settings(other.email).connection).isNull()
        assertThatThrownBy {service.owned(other.email)}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `stale settings invalid fields incomplete credentials and inactive providers block changes`() {
        service.save(user.email,PersonalCalendarRequest("calendar-caldav",fields))
        assertThatThrownBy {service.save(user.email,PersonalCalendarRequest("calendar-caldav",fields))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.save(user.email,PersonalCalendarRequest("calendar-caldav",mapOf("ownerId" to "other"),revision=0))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {service.save(user.email,PersonalCalendarRequest("calendar-caldav",clearSecrets=setOf("password"),enabled=true,revision=0))}.isInstanceOf(BadRequestException::class.java)
        doThrow(NotFoundException("Disabled")).`when`(registry).requireCapability("calendar","calendar.personal-sync","calendar-caldav","USER")
        assertThatThrownBy {service.save(user.email,PersonalCalendarRequest("calendar-caldav",enabled=true,revision=0))}.isInstanceOf(NotFoundException::class.java)
        assertThat(service.save(user.email,PersonalCalendarRequest("calendar-caldav",enabled=false,revision=0)).enabled).isFalse()
    }
    @Test fun `status revisions do not invalidate a settings draft and disconnect retains private imported records`() {
        service.save(user.email,PersonalCalendarRequest("calendar-caldav",fields))
        stored!!.revision=15 // Background status/refresh-token update; config revision stays zero.
        val view=service.save(user.email,PersonalCalendarRequest("calendar-caldav",enabled=false,revision=0))
        assertThat(view.revision).isEqualTo(1)
        val imported=CalendarEvent(ownerId=user.id,personal=true,originConnectionId=stored!!.id)
        `when`(events.findAllByOriginConnectionId(stored!!.id)).thenReturn(listOf(imported))
        service.disconnect(user.email,1)
        assertThat(imported.originConnectionId).isNull();assertThat(imported.syncExcluded).isTrue();assertThat(imported.personal).isTrue()
        verify(links).deleteByConnection_Id(stored!!.id)
        verify(connections).delete(stored!!)
    }
    @Test fun `per user Microsoft supports delegated account aliases and CalDAV stays inside HTTPS`() {
        PersonalCalendarSchemas.validate("calendar-microsoft",mapOf("tenantId" to "common","clientId" to "12345678-1234-1234-1234-123456789abc"))
        assertThatThrownBy {PersonalCalendarSchemas.validate("calendar-microsoft",mapOf("tenantId" to "../private"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {PersonalCalendarSchemas.validate("calendar-caldav",mapOf("calendarUrl" to "http://calendar.example.test"))}.isInstanceOf(BadRequestException::class.java)
    }
}
