package com.starter.plugins.calendar

import com.starter.plugins.*
import com.starter.plugins.integrations.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

@Component
class CalDavCalendarProvider(private val registry: PluginRegistry, private val settings: PluginConfigurationService,
    private val http: IntegrationHttpClient) : CalendarProvider {
    override val pluginId = "calendar-caldav"
    private fun connection(): Pair<String,Map<String,String>> {
        val values = calendarValues(pluginId,registry,settings)
        val basic = Base64.getEncoder().encodeToString("${values.getValue("username")}:${values.getValue("password")}".toByteArray(Charsets.UTF_8))
        return values.getValue("calendarUrl").trimEnd('/') + "/" to mapOf("Authorization" to "Basic $basic")
    }
    override fun events(from: Instant, until: Instant): CalendarWindow {
        val (base,headers) = connection()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        val start = stamp.format(from); val end = stamp.format(until)
        val xml = """<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><c:calendar-data><c:expand start="$start" end="$end"/></c:calendar-data></d:prop><c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"><c:time-range start="$start" end="$end"/></c:comp-filter></c:comp-filter></c:filter></c:calendar-query>"""
        val response = http.scoped("REPORT",base,headers=headers + ("Depth" to "1"),body=xml,contentType="application/xml")
        return parse(response.text,from,until)
    }
    override fun publish(event: CalendarEvent): CalendarPublication {
        val (base,headers) = connection()
        val response = http.scoped("PUT",base,"${event.id}.ics",headers + ("If-None-Match" to "*"),
            IcalendarCodec.export(listOf(CalendarService.view(event))),"text/calendar; charset=utf-8",setOf(412))
        return if (response.status == 412) CalendarPublication("This event is already present. Publishing does not update the remote copy.") else CalendarPublication()
    }
    companion object {
        internal fun parse(xml: String, from: Instant, until: Instant): CalendarWindow {
            try {
                val document = CalendarXml.parse(xml)
                val nodes = document.getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav","calendar-data")
                val windows = (0 until minOf(nodes.length,101)).map { IcalendarCodec.parse(nodes.item(it).textContent,"calendar-caldav") }
                val events = windows.flatMap { it.events }.filter {
                    val start = if (it.allDay) java.time.LocalDate.parse(it.start).atStartOfDay(ZoneOffset.UTC).toInstant() else Instant.parse(it.start)
                    val end = if (it.allDay) java.time.LocalDate.parse(it.end).atStartOfDay(ZoneOffset.UTC).toInstant() else Instant.parse(it.end)
                    start < until && end > from
                }
                return CalendarWindow(events.take(100),nodes.length > 100 || events.size > 100 || windows.any { it.truncated },windows.firstNotNullOfOrNull { it.notice })
            } catch (_: Exception) { throw IntegrationUnavailable("CalDAV returned unsupported calendar data") }
        }
    }
}
