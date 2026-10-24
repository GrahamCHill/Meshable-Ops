package com.starter.plugins.calendar

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.*
import com.starter.plugins.integrations.*
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MicrosoftCalendarProvider(private val registry: PluginRegistry, private val settings: PluginConfigurationService,
    private val http: IntegrationHttpClient, private val mapper: ObjectMapper) : CalendarProvider {
    override val pluginId = "calendar-microsoft"
    private fun connection(): Pair<String,Map<String,String>> {
        val values = calendarValues(pluginId,registry,settings)
        val shared = values["credentialPlugin"]
        val credentials = if (shared == null) values else {
            registry.requireCapability(pluginId,"microsoft.graph.credentials",shared,"ADMIN")
            settings.current(shared).also {
                if (listOf("tenantId","clientId","clientSecret").any { key -> it[key].isNullOrBlank() }) throw com.starter.exception.BadRequestException("Complete the shared Microsoft credentials first")
                registry.validateConfiguration(shared,it)
            }
        }
        val token = accessToken(http.request("POST","https://login.microsoftonline.com/${credentials.getValue("tenantId")}/oauth2/v2.0/token",
            body=calendarForm(mapOf("client_id" to credentials.getValue("clientId"),"client_secret" to credentials.getValue("clientSecret"),
                "scope" to "https://graph.microsoft.com/.default","grant_type" to "client_credentials")),contentType="application/x-www-form-urlencoded"))
        registry.requireAvailable(pluginId,"ADMIN")
        shared?.let { registry.requireCapability(pluginId,"microsoft.graph.credentials",it,"ADMIN") }
        val calendar = values["calendarId"]?.let { "calendars/${encodeCalendar(it)}/" }.orEmpty()
        return "https://graph.microsoft.com/v1.0/users/${encodeCalendar(values.getValue("mailbox"))}/$calendar" to mapOf("Authorization" to "Bearer $token","Prefer" to "outlook.timezone=\"UTC\"")
    }
    override fun events(from: Instant, until: Instant): CalendarWindow {
        val (base,headers) = connection()
        val body = http.request("GET","${base}calendarView?startDateTime=${encodeCalendar(from.toString())}&endDateTime=${encodeCalendar(until.toString())}&\$top=100&\$select=id,subject,bodyPreview,start,end,isAllDay,isCancelled",headers).body
            ?: throw IntegrationUnavailable("Microsoft returned no calendar events")
        if (!body.path("value").isArray) throw IntegrationUnavailable("Microsoft returned an unsupported calendar response")
        return CalendarWindow(body.path("value").take(100).mapNotNull { remoteCalendarEvent(it,pluginId,true) },body.has("@odata.nextLink"))
    }
    override fun publish(event: CalendarEvent): CalendarPublication {
        val (base,headers) = connection()
        http.request("POST","${base}events",headers,mapper.writeValueAsString(mapOf("subject" to event.title,
            "body" to mapOf("contentType" to "text","content" to event.description), "isAllDay" to event.allDay,
            "start" to mapOf("dateTime" to event.startsAt.toString(),"timeZone" to "UTC"),
            "end" to mapOf("dateTime" to event.endsAt.toString(),"timeZone" to "UTC"),"transactionId" to event.id.toString())))
        return CalendarPublication()
    }
}
