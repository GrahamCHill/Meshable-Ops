package com.starter.plugins.calendar

import java.time.*

/** Reconcile only rows belonging to the locked connection. Provider wins conflicts; preserve the local draft. */
class CalendarSyncReconciler(private val events: CalendarEventRepository,private val links: CalendarSyncLinkRepository) {
    var conflicts = 0; private set
    fun same(event: CalendarEvent,remote: RemoteCalendarEvent): Boolean {
        val view = remote.event
        return event.deletedAt==null && event.title==view.title.take(160) && event.description==view.description.take(4000) &&
            event.startsAt==instant(view.start,view.allDay) && event.endsAt==instant(view.end,view.allDay) && event.allDay==view.allDay
    }
    fun pull(link: CalendarSyncLink,remote: RemoteCalendarEvent) {
        if (link.event.syncExcluded || (link.remoteVersion==remote.version && !link.remoteDeleted)) return
        if (link.localRevision!=link.event.revision && !same(link.event,remote)) preserve(link.event)
        apply(link.event,remote)
        link.remoteVersion=remote.version;link.localRevision=link.event.revision;link.remoteDeleted=false
        links.save(link)
    }
    fun removed(link: CalendarSyncLink) {
        if (link.event.syncExcluded || link.remoteDeleted) return
        if (link.localRevision!=link.event.revision && link.event.deletedAt==null) preserve(link.event)
        link.event.deletedAt=Instant.now();events.saveAndFlush(link.event)
        link.localRevision=link.event.revision;link.remoteDeleted=true;links.save(link)
    }
    fun apply(event: CalendarEvent,remote: RemoteCalendarEvent) {
        val view=remote.event
        val input=CalendarEventRequest(view.title.take(160),view.description.take(4000),instant(view.start,view.allDay),instant(view.end,view.allDay),view.allDay)
        CalendarService.validateEvent(input)
        event.title=input.title;event.description=input.description;event.startsAt=input.start;event.endsAt=input.end;event.allDay=input.allDay;event.deletedAt=null
        val details = CalendarEventDetails.read(event)
        event.detailsJson = CalendarEventDetails.write(details.copy(calendarName=view.calendarName,
            documents=(details.documents+view.documents).distinctBy { it.url }.take(20), participants=view.participants.ifEmpty { details.participants }))
        events.saveAndFlush(event)
    }
    fun preserve(event: CalendarEvent) {
        conflicts++
        events.saveAndFlush(CalendarEvent(ownerId=event.ownerId,title=event.title.take(140)+" (conflict copy)",description=event.description,
            startsAt=event.startsAt,endsAt=event.endsAt,allDay=event.allDay,syncExcluded=true,personal=event.personal,detailsJson=event.detailsJson))
    }
    private fun instant(value: String,allDay: Boolean) = if (allDay) LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() else Instant.parse(value)
}
