package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.calendar.*
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.*
import java.util.*

class PersonalCalendarSyncTest {
    private val actor=User(email="member@example.test")
    private val connection=PersonalCalendarConnection(ownerId=actor.id,providerId="calendar-google",enabled=true)
    private val connections=mock(PersonalCalendarRepository::class.java)
    private val registry=mock(PluginRegistry::class.java)
    private val actors=mock(PluginActor::class.java)
    private val users=mock(UserRepository::class.java)
    private val settings=mock(PersonalCalendarSettingsService::class.java)
    private val factory=mock(PersonalCalendarRemoteFactory::class.java)
    private val records=mutableListOf<CalendarEvent>()
    private val ledger=mutableListOf<CalendarSyncLink>()
    private val events=mock(CalendarEventRepository::class.java) { call -> when(call.method.name) {
        "saveAndFlush" -> (call.arguments[0] as CalendarEvent).also { if (it in records) it.revision++ else records+=it }
        "findById" -> Optional.ofNullable(records.find { it.id==call.arguments[0] })
        "forSync" -> records.toList()
        else -> org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    } }
    private val links=mock(CalendarSyncLinkRepository::class.java) { call -> when(call.method.name) {
        "findByConnection_Id" -> ledger.toList()
        "save","saveAndFlush" -> (call.arguments[0] as CalendarSyncLink).also { if (it !in ledger) ledger+=it }
        else -> org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    } }
    private val remote=FakeRemote()
    private val mapper=jacksonObjectMapper()
    private val engine=PersonalCalendarSyncEngine(connections,links,events,users,actors,registry,settings,factory,mapper)
    private val start=LocalDate.now(ZoneOffset.UTC).atTime(9,0).toInstant(ZoneOffset.UTC)
    init {
        `when`(connections.locked(connection.id)).thenReturn(Optional.of(connection))
        `when`(users.findById(actor.id)).thenReturn(Optional.of(actor))
        `when`(actors.fresh(actor.email)).thenReturn(actor)
        `when`(settings.values(connection)).thenReturn(emptyMap())
        `when`(factory.open(connection.providerId,emptyMap())).thenReturn(remote)
    }
    private fun event(title: String="Local") = CalendarEvent(ownerId=actor.id,title=title,startsAt=start,endsAt=start.plusSeconds(3600))
    private fun external(id: String="remote",title: String="Remote",version: String="v1",hint: UUID?=null)=RemoteCalendarEvent(id,version,
        CalendarEventView(id,title,"",start.toString(),start.plusSeconds(3600).toString(),false),hint)
    private fun linked(local: CalendarEvent,item: RemoteCalendarEvent): CalendarSyncLink {
        records+=local;remote.rows[item.id]=item
        return CalendarSyncLink(connection=connection,event=local,remoteId=item.id,remoteVersion=item.version,localRevision=local.revision).also { ledger+=it }
    }
    @Test fun `sync imports private owned events and publishes only the owners included local events`() {
        remote.rows["remote"]=external()
        records+=event();records+=event("Excluded").apply {syncExcluded=true};records+=CalendarEvent(ownerId=UUID.randomUUID(),title="Other member")
        engine.sync(connection.id)
        assertThat(remote.writes).containsExactly("create:Local")
        val imported=records.single { it.title=="Remote" }
        assertThat(imported.ownerId).isEqualTo(actor.id);assertThat(imported.personal).isTrue();assertThat(imported.originConnectionId).isEqualTo(connection.id)
        assertThat(ledger).hasSize(2)
        assertThat(connection.lastSyncAt).isNotNull();assertThat(connection.status).contains("complete")
    }
    @Test fun `local edits update the linked remote event with its revision`() {
        val local=event();val link=linked(local,external(title="Local"))
        local.title="Edited locally";local.revision++
        engine.sync(connection.id)
        assertThat(remote.writes).containsExactly("update:remote:v1")
        assertThat(remote.rows.getValue("remote").event.title).isEqualTo("Edited locally")
        assertThat(link.localRevision).isEqualTo(local.revision)
    }
    @Test fun `remote edits update the local event without publishing it again`() {
        val local=event();val link=linked(local,external(title="Local"))
        remote.rows["remote"]=external(title="Edited remotely",version="v2")
        engine.sync(connection.id)
        assertThat(local.title).isEqualTo("Edited remotely");assertThat(link.remoteVersion).isEqualTo("v2")
        assertThat(remote.writes).isEmpty()
    }
    @Test fun `simultaneous edits preserve a local only conflict copy and apply provider changes`() {
        val local=event();linked(local,external(title="Local"))
        local.title="Unsynced draft";local.revision++
        remote.rows["remote"]=external(title="Remote edit",version="v2")
        engine.sync(connection.id)
        assertThat(local.title).isEqualTo("Remote edit")
        assertThat(records.single { it.title=="Unsynced draft (conflict copy)" }.syncExcluded).isTrue()
        assertThat(connection.status).contains("1 conflicts")
        assertThat(remote.writes).isEmpty()
    }
    @Test fun `local deletions propagate and remote deletions create local tombstones`() {
        val local=event();val link=linked(local,external(title="Local"))
        local.deletedAt=Instant.now();local.revision++
        engine.sync(connection.id)
        assertThat(remote.writes).containsExactly("delete:remote:v1");assertThat(link.remoteDeleted).isTrue()
        val second=event("Second");val secondLink=linked(second,external("gone","Second"))
        remote.rows.remove("gone");remote.writes.clear()
        engine.sync(connection.id)
        assertThat(second.deletedAt).isNotNull();assertThat(secondLink.remoteDeleted).isTrue()
        assertThat(remote.writes).isEmpty()
    }
    @Test fun `conditional write conflicts retain the local change and schedule reconciliation`() {
        val local=event();val link=linked(local,external(title="Local"));local.revision++
        local.title="Keep draft";remote.conflict=true
        engine.sync(connection.id)
        assertThat(local.title).isEqualTo("Keep draft");assertThat(link.localRevision).isZero()
        assertThat(connection.status).contains("pending")
    }
    @Test fun `disabled sync disabled provider and deactivated account make no provider calls`() {
        connection.enabled=false
        assertThatThrownBy { engine.sync(connection.id) }.isInstanceOf(BadRequestException::class.java)
        connection.enabled=true
        doThrow(NotFoundException("Disabled")).`when`(registry).requireCapability("calendar","calendar.personal-sync",connection.providerId,"USER")
        assertThatThrownBy { engine.sync(connection.id) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(factory)
        reset(registry)
        `when`(actors.fresh(actor.email)).thenThrow(ForbiddenException("Inactive"))
        assertThatThrownBy { engine.sync(connection.id) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(factory)
    }
    @Test fun `remote event identity hints cannot attach to another members event`() {
        val other=event("Another member").apply { personal=true }
        val foreign=CalendarEvent(id=other.id,ownerId=UUID.randomUUID(),title=other.title)
        records+=foreign;remote.rows["foreign"]=external("foreign",hint=foreign.id)
        engine.sync(connection.id)
        assertThat(foreign.title).isEqualTo("Another member")
        assertThat(ledger.single().event.ownerId).isEqualTo(actor.id)
        assertThat(ledger.single().event.id).isNotEqualTo(foreign.id)
    }
    @Test fun `failure status stores no provider exception or credential details`() {
        engine.failed(connection.id)
        assertThat(connection.status).contains("permissions").doesNotContain("private-token")
        assertThat(connection.nextSyncAt).isAfter(Instant.now())
    }
    private class FakeRemote : PersonalCalendarRemote {
        override val credentials=mapOf("refreshToken" to "rotated-private-token")
        override val remaining=20
        val rows=mutableMapOf<String,RemoteCalendarEvent>()
        val writes=mutableListOf<String>();var conflict=false
        override fun list(from: Instant,until: Instant)=RemoteCalendarPage(rows.values.toList())
        override fun get(id: String)=rows[id]
        override fun create(event: CalendarEvent): RemoteCalendarEvent {writes+="create:${event.title}";return RemoteCalendarEvent("created-${event.id}","new",CalendarService.view(event),event.id).also {rows[it.id]=it}}
        override fun update(id: String,version: String,event: CalendarEvent): RemoteCalendarEvent {
            if (conflict) throw RemoteCalendarConflict()
            writes+="update:$id:$version";return RemoteCalendarEvent(id,"next",CalendarService.view(event)).also {rows[id]=it}
        }
        override fun delete(id: String,version: String) {writes+="delete:$id:$version";rows.remove(id)}
    }
}
