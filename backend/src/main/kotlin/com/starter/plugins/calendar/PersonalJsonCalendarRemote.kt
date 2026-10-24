package com.starter.plugins.calendar

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.integrations.*
import java.time.*
import java.util.UUID

class PersonalJsonCalendarRemote(http: IntegrationHttpClient,private val mapper: ObjectMapper,private val provider: String,
    override val credentials: Map<String,String>,token: String) : BoundedCalendarRemote(http) {
    private val microsoft = provider == "calendar-microsoft"
    private val base = if (microsoft) "https://graph.microsoft.com/v1.0/me/" + (credentials["calendarId"]?.let { "calendars/${encodeCalendar(it)}/" } ?: "")
        else "https://www.googleapis.com/calendar/v3/calendars/${encodeCalendar(credentials.getValue("calendarId"))}/"
    private val headers = mapOf("Authorization" to "Bearer $token") + if (microsoft) mapOf("Prefer" to "outlook.timezone=\"UTC\"") else emptyMap()
    override fun list(from: Instant,until: Instant): RemoteCalendarPage {
        val query = if (microsoft) "calendarView?startDateTime=${encodeCalendar(from.toString())}&endDateTime=${encodeCalendar(until.toString())}&\$top=100&\$select=id,subject,bodyPreview,start,end,isAllDay,isCancelled,type,transactionId,changeKey,attendees"
            else "events?timeMin=${encodeCalendar(from.toString())}&timeMax=${encodeCalendar(until.toString())}&singleEvents=true&maxResults=100"
        val response = request("GET",base,query,headers)
        val node = response.body ?: throw IntegrationUnavailable("Calendar provider returned no event data")
        val array = node.path(if (microsoft) "value" else "items")
        if (!array.isArray) throw IntegrationUnavailable("Calendar provider returned an unsupported event listing")
        var skipped = false
        val events = array.take(100).mapNotNull { if (unsupported(it)) {skipped=true;null} else parse(it) }
        return RemoteCalendarPage(events,if (skipped || node.has(if (microsoft) "@odata.nextLink" else "nextPageToken")) "Some events were omitted: recurring/invited events or the provider's 100-event page limit. Use the provider for the complete calendar." else null)
    }
    override fun get(id: String): RemoteCalendarEvent? {
        val response = request("GET",base,"events/${encodeCalendar(id)}",headers)
        if (response.status in setOf(404,410)) return null
        val node = response.body ?: throw IntegrationUnavailable("Calendar provider returned no event data")
        if (unsupported(node)) throw IntegrationUnavailable("A linked event became recurring or unsupported. Resolve it in your provider before resuming sync.")
        return parse(node)
    }
    override fun create(event: CalendarEvent): RemoteCalendarEvent {
        val id = "cafe"+event.id.toString().replace("-","")
        val payload = payload(event).toMutableMap()
        if (microsoft) payload["transactionId"]=event.id.toString() else payload["id"]=id
        val response = request("POST",base,"events",headers,mapper.writeValueAsString(payload))
        if (response.status == 409 && !microsoft) return get(id) ?: throw IntegrationUnavailable("Existing event could not be read")
        if (response.status !in 200..299) throw IntegrationUnavailable("Calendar provider could not create the event")
        return response.body?.let(::parse) ?: throw IntegrationUnavailable("Calendar provider did not return the created event")
    }
    override fun update(id: String,version: String,event: CalendarEvent): RemoteCalendarEvent {
        val response = request("PATCH",base,"events/${encodeCalendar(id)}",headers + ("If-Match" to version),mapper.writeValueAsString(payload(event)))
        if (response.status in setOf(404,410,412)) throw RemoteCalendarConflict()
        if (response.status !in 200..299) throw IntegrationUnavailable("Calendar provider could not update the event")
        return response.body?.let(::parse) ?: throw IntegrationUnavailable("Calendar provider did not return the updated event")
    }
    override fun delete(id: String,version: String) {
        val response = request("DELETE",base,"events/${encodeCalendar(id)}",headers + ("If-Match" to version))
        if (response.status == 412) throw RemoteCalendarConflict()
        if (response.status !in 200..299 && response.status !in setOf(404,410)) throw IntegrationUnavailable("Calendar provider could not delete the event")
    }
    private fun unsupported(node: JsonNode) = node.path("attendees").size()>0 || node.has("recurringEventId") || (microsoft && node.path("type").asText("singleInstance") != "singleInstance")
    private fun parse(node: JsonNode): RemoteCalendarEvent? {
        val event = remoteCalendarEvent(node,provider,microsoft) ?: return null
        val id = node.path("id").asText()
        if (id.isBlank() || id.length > 1000) throw IntegrationUnavailable("Calendar provider returned an unsupported event ID")
        val identity = if (microsoft) node.path("transactionId").asText() else id.takeIf { it.startsWith("cafe") && it.length==36 }?.drop(4)?.let {
            "${it.take(8)}-${it.substring(8,12)}-${it.substring(12,16)}-${it.substring(16,20)}-${it.substring(20)}"
        }
        return RemoteCalendarEvent(id,version(node.path(if (microsoft) "@odata.etag" else "etag").asText()),event,runCatching { UUID.fromString(identity) }.getOrNull())
    }
    private fun payload(event: CalendarEvent): Map<String,Any> {
        fun time(instant: Instant) = if (microsoft) mapOf("dateTime" to instant.toString(),"timeZone" to "UTC") else mapOf("date" to (if (event.allDay) instant.atOffset(ZoneOffset.UTC).toLocalDate().toString() else null),"dateTime" to (if (event.allDay) null else instant.toString()))
        return if (microsoft) mapOf("subject" to event.title,"body" to mapOf("contentType" to "text","content" to event.description),"isAllDay" to event.allDay,"start" to time(event.startsAt),"end" to time(event.endsAt))
            else mapOf("summary" to event.title,"description" to event.description,"start" to time(event.startsAt),"end" to time(event.endsAt))
    }
}
