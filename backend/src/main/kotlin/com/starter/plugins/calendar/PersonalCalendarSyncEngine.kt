package com.starter.plugins.calendar

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.*
import com.starter.repository.postgres.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class PersonalCalendarSyncEngine(private val connections: PersonalCalendarRepository,private val links: CalendarSyncLinkRepository,
    private val events: CalendarEventRepository,private val users: UserRepository,private val actors: PluginActor,private val registry: PluginRegistry,
    private val settings: PersonalCalendarSettingsService,private val factory: PersonalCalendarRemoteFactory,private val mapper: ObjectMapper) {
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun sync(id: UUID) {
        val connection=connections.locked(id).orElse(null) ?: return
        if (!connection.enabled) throw BadRequestException("Enable your personal connection before syncing")
        val owner=users.findById(connection.ownerId).orElse(null) ?: throw ForbiddenException("Account unavailable")
        fun check() {
            val actor=actors.fresh(owner.email)
            if (actor.id!=connection.ownerId) throw ForbiddenException("Account changed")
            listOf("plugin_calendar_events","plugin_calendar_connections","plugin_calendar_sync_links").forEach { registry.requireTable("calendar",it,actor.role,true) }
            registry.requireCapability("calendar","calendar.personal-sync",connection.providerId,actor.role)
        }
        check()
        val remote=factory.open(connection.providerId,settings.values(connection))
        check()
        connection.credentialsJson=mapper.writeValueAsString(remote.credentials) // Persist rotated offline grants encrypted.
        val from=LocalDate.now(ZoneOffset.UTC).minusDays(30).atStartOfDay(ZoneOffset.UTC).toInstant()
        val until=from.plus(Duration.ofDays(90))
        val page=remote.list(from,until)
        check()
        val reconciler=CalendarSyncReconciler(events,links)
        val ledger=links.findByConnection_Id(connection.id).toMutableList()
        if (ledger.any { it.event.ownerId!=owner.id }) throw ForbiddenException("Sync relationships must belong to their account")
        var pending=false
        for (item in page.events) {
            val existing=ledger.find { it.remoteId==item.id }
            if (existing!=null) {reconciler.pull(existing,item);continue}
            if (ledger.size>=1000) {pending=true;break}
            val hinted=item.localId?.let { events.findById(it).orElse(null) }?.takeIf { it.ownerId==owner.id && (it.originConnectionId==null || it.originConnectionId==connection.id) }
            if (hinted?.syncExcluded==true) continue
            // A remote hint cannot create a second remote mapping to the same local event.
            if (hinted!=null && ledger.any { it.event.id==hinted.id }) continue
            val local=hinted ?: CalendarEvent(ownerId=owner.id,originConnectionId=connection.id,personal=true)
            val link=CalendarSyncLink(connection=connection,event=local,remoteId=item.id,remoteVersion="",localRevision=if (hinted==null) local.revision else -1)
            if (hinted!=null && !reconciler.same(local,item)) reconciler.preserve(local)
            reconciler.apply(local,item)
            link.remoteVersion=item.version;link.localRevision=local.revision
            ledger+=links.saveAndFlush(link)
        }
        // A time-window omission is never treated as deletion without a direct, authenticated GET.
        val listed=page.events.map { it.id }.toSet()
        for (link in ledger.filter { !it.remoteDeleted && !it.event.syncExcluded && it.remoteId !in listed && it.event.startsAt<until && it.event.endsAt>from }) {
            if (remote.remaining<4) {pending=true;break}
            check()
            val item=remote.get(link.remoteId)
            if (item==null) reconciler.removed(link) else reconciler.pull(link,item)
        }
        val local=events.forSync(owner.id,from,until,PageRequest.of(0,501))
        if (local.size>500) pending=true
        val work=(local.take(500)+ledger.map { it.event }).distinctBy { it.id }
        for (event in work) {
            if (event.ownerId!=owner.id || event.syncExcluded || (event.originConnectionId!=null && event.originConnectionId!=connection.id)) continue
            val link=ledger.find { it.event.id==event.id }
            if (link?.remoteDeleted==true || (link!=null && link.localRevision==event.revision) || (link==null && event.deletedAt!=null)) continue
            if (remote.remaining<4 || (link==null && ledger.size>=1000)) {pending=true;break}
            check()
            try {
                if (event.deletedAt!=null && link!=null) {
                    remote.delete(link.remoteId,link.remoteVersion)
                    link.remoteDeleted=true;link.localRevision=event.revision;links.save(link)
                } else {
                    val item=if (link==null) remote.create(event) else remote.update(link.remoteId,link.remoteVersion,event)
                    val saved=link ?: CalendarSyncLink(connection=connection,event=event,remoteId=item.id)
                    if (!reconciler.same(event,item)) {reconciler.preserve(event);reconciler.apply(event,item)}
                    saved.remoteVersion=item.version;saved.localRevision=event.revision;saved.remoteDeleted=false
                    links.saveAndFlush(saved)
                    if (link==null) ledger+=saved
                }
            } catch (_:RemoteCalendarConflict) { pending=true } // Conditional writes leave the provider untouched; retry reconciliation.
        }
        check()
        connection.lastSyncAt=Instant.now();connection.nextSyncAt=Instant.now().plusSeconds(if (pending) 60 else 300)
        connection.status=listOfNotNull("Two-way sync complete",if (pending) "More work pending; another cycle will continue" else null,
            if (reconciler.conflicts>0) "${reconciler.conflicts} conflicts preserved as local-only copies" else null,page.notice).joinToString(". ").take(500)
        connections.saveAndFlush(connection)
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun failed(id: UUID) {
        val row=connections.locked(id).orElse(null) ?: return
        row.nextSyncAt=Instant.now().plusSeconds(300)
        row.status="Sync paused by an access or provider error. Check your connection and permissions; retry in five minutes or use Sync now."
        connections.save(row) // Never store provider bodies, URLs, event text, or token errors.
    }
}
