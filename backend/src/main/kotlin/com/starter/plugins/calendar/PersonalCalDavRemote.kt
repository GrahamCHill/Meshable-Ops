package com.starter.plugins.calendar

import com.starter.plugins.integrations.*
import org.w3c.dom.Element
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

class PersonalCalDavRemote(http: IntegrationHttpClient,override val credentials: Map<String,String>) : BoundedCalendarRemote(http) {
    private val base = credentials.getValue("calendarUrl").trimEnd('/') + "/"
    private val originals = mutableMapOf<String,String>()
    private val headers = mapOf("Authorization" to "Basic "+Base64.getEncoder().encodeToString("${credentials.getValue("username")}:${credentials.getValue("password")}".toByteArray(Charsets.UTF_8)))
    override fun list(from: Instant,until: Instant): RemoteCalendarPage {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        val xml = """<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:getetag/><c:calendar-data/></d:prop><c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"><c:time-range start="${stamp.format(from)}" end="${stamp.format(until)}"/></c:comp-filter></c:comp-filter></c:filter></c:calendar-query>"""
        val response = request("REPORT",base,headers=headers + ("Depth" to "1"),body=xml,contentType="application/xml")
        if (response.status !in 200..299) throw IntegrationUnavailable("CalDAV could not read your selected calendar")
        val document = CalendarXml.parse(response.text)
        val nodes = document.getElementsByTagNameNS("DAV:","response")
        var skipped = nodes.length>100
        val events = (0 until minOf(nodes.length,100)).mapNotNull { index ->
            val node = nodes.item(index) as Element
            val data = node.getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav","calendar-data").item(0)?.textContent
            val href = node.getElementsByTagNameNS("DAV:","href").item(0)?.textContent
            val etag = node.getElementsByTagNameNS("DAV:","getetag").item(0)?.textContent
            if (data==null || href==null || etag==null || unsupported(data)) { skipped=true;null }
            else parse(resourceId(base,href),etag,data).also { if (it==null) skipped=true }
        }
        return RemoteCalendarPage(events,if (skipped) "Some events were omitted: recurring/invited events, unsupported calendar data, or the 100-resource limit." else null)
    }
    override fun get(id: String): RemoteCalendarEvent? {
        resourceId(base,id)
        val response = request("GET",base,id,headers)
        if (response.status in setOf(404,410)) return null
        if (unsupported(response.text)) throw IntegrationUnavailable("A linked event became recurring or invited. Resolve it in the provider before resuming sync.")
        return parse(id,response.headers["ETag"].orEmpty(),response.text) ?: throw IntegrationUnavailable("CalDAV returned an unsupported linked event")
    }
    override fun create(event: CalendarEvent): RemoteCalendarEvent {
        val id = "${event.id}.ics"
        val response = request("PUT",base,id,headers + ("If-None-Match" to "*"),IcalendarCodec.export(listOf(CalendarService.view(event))),"text/calendar; charset=utf-8")
        if (response.status !in 200..299 && response.status!=412) throw IntegrationUnavailable("CalDAV could not create your event")
        return get(id) ?: throw IntegrationUnavailable("CalDAV did not return the created event")
    }
    override fun update(id: String,version: String,event: CalendarEvent): RemoteCalendarEvent {
        resourceId(base,id)
        if (id !in originals) get(id) ?: throw RemoteCalendarConflict()
        val original=originals[id] ?: throw IntegrationUnavailable("CalDAV returned no event data")
        val response = request("PUT",base,id,headers + ("If-Match" to version),IcalendarCodec.update(original,CalendarService.view(event)),"text/calendar; charset=utf-8")
        if (response.status in setOf(404,410,412)) throw RemoteCalendarConflict()
        if (response.status !in 200..299) throw IntegrationUnavailable("CalDAV could not update your event")
        return get(id) ?: throw RemoteCalendarConflict()
    }
    override fun delete(id: String,version: String) {
        resourceId(base,id)
        val response = request("DELETE",base,id,headers + ("If-Match" to version))
        if (response.status==412) throw RemoteCalendarConflict()
        if (response.status !in 200..299 && response.status !in setOf(404,410)) throw IntegrationUnavailable("CalDAV could not delete your event")
    }
    private fun parse(id: String,etag: String,data: String): RemoteCalendarEvent? {
        val parsed = IcalendarCodec.parse(data,"calendar-caldav")
        if (parsed.events.size!=1 || parsed.truncated) return null
        val uid = IcalendarCodec.uid(data) ?: return null
        originals[id]=data
        return RemoteCalendarEvent(id,version(etag),parsed.events.single(),if (uid.endsWith("@weaveops")) runCatching { UUID.fromString(uid.removeSuffix("@weaveops")) }.getOrNull() else null)
    }
    private fun unsupported(text: String) = text.lineSequence().any { it.uppercase().startsWith("ATTENDEE") || it.startsWith("RRULE") || it.startsWith("RECURRENCE-ID") || it.startsWith("RDATE") }
    companion object {
        internal fun resourceId(base: String,href: String): String {
            val root = IntegrationHttpClient.endpoint(base.trimEnd('/')+"/")
            val target = runCatching { root.resolve(href) }.getOrNull() ?: throw IntegrationUnavailable("Unsupported CalDAV resource path")
            val prefix = root.rawPath
            if (target.scheme!=root.scheme || target.rawAuthority!=root.rawAuthority || target.query!=null || target.fragment!=null || !target.rawPath.startsWith(prefix)) throw IntegrationUnavailable("CalDAV resource escaped the selected calendar")
            val relative = target.rawPath.removePrefix(prefix)
            if (relative.isBlank() || '/' in relative) throw IntegrationUnavailable("Unsupported CalDAV resource path")
            IntegrationHttpClient.endpoint(target.toString())
            return relative
        }
    }
}
