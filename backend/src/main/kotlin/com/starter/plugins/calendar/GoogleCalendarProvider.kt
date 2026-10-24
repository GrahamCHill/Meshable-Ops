package com.starter.plugins.calendar

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.*
import com.starter.plugins.integrations.*
import org.springframework.stereotype.Component
import java.time.*

@Component
class GoogleCalendarProvider(private val registry: PluginRegistry, private val settings: PluginConfigurationService,
    private val http: IntegrationHttpClient, private val mapper: ObjectMapper) : CalendarProvider {
    override val pluginId = "calendar-google"
    private fun connection(): Pair<String,Map<String,String>> {
        val values = calendarValues(pluginId,registry,settings)
        val token = accessToken(http.request("POST","https://oauth2.googleapis.com/token",body=calendarForm(mapOf(
            "client_id" to values.getValue("clientId"),"client_secret" to values.getValue("clientSecret"),"refresh_token" to values.getValue("refreshToken"),"grant_type" to "refresh_token")),contentType="application/x-www-form-urlencoded"))
        registry.requireAvailable(pluginId,"ADMIN")
        return "https://www.googleapis.com/calendar/v3/calendars/${encodeCalendar(values.getValue("calendarId"))}/events" to mapOf("Authorization" to "Bearer $token")
    }
    override fun events(from: Instant, until: Instant): CalendarWindow {
        val (base,headers) = connection()
        val body = http.request("GET","$base?timeMin=${encodeCalendar(from.toString())}&timeMax=${encodeCalendar(until.toString())}&singleEvents=true&orderBy=startTime&maxResults=100",headers).body
            ?: throw IntegrationUnavailable("Google returned no calendar events")
        if (!body.path("items").isArray) throw IntegrationUnavailable("Google returned an unsupported calendar response")
        return CalendarWindow(body.path("items").take(100).mapNotNull { remoteCalendarEvent(it,pluginId,false) },body.has("nextPageToken"))
    }
    override fun publish(event: CalendarEvent): CalendarPublication {
        val (base,headers) = connection()
        val remoteId = "cafe" + event.id.toString().replace("-","") // Google IDs: base32hex; stable across retries.
        fun time(instant: Instant) = if (event.allDay) mapOf("date" to instant.atOffset(ZoneOffset.UTC).toLocalDate().toString()) else mapOf("dateTime" to instant.toString())
        val response = http.scoped("POST",base,headers=headers,body=mapper.writeValueAsString(mapOf("id" to remoteId,"summary" to event.title,
            "description" to event.description,"start" to time(event.startsAt),"end" to time(event.endsAt))),allowedStatuses=setOf(409))
        if (response.status == 409) return CalendarPublication("This event is already present. Publishing does not update the remote copy.")
        return CalendarPublication()
    }
}
