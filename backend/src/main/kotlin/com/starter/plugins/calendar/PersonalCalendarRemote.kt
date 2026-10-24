package com.starter.plugins.calendar

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.integrations.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class RemoteCalendarEvent(val id: String,val version: String,val event: CalendarEventView,val localId: UUID? = null)
data class RemoteCalendarPage(val events: List<RemoteCalendarEvent>,val notice: String? = null)
class RemoteCalendarConflict : RuntimeException()
interface PersonalCalendarRemote {
    val credentials: Map<String,String>
    val remaining: Int
    fun list(from: Instant,until: Instant): RemoteCalendarPage
    fun get(id: String): RemoteCalendarEvent?
    fun create(event: CalendarEvent): RemoteCalendarEvent
    fun update(id: String,version: String,event: CalendarEvent): RemoteCalendarEvent
    fun delete(id: String,version: String)
}

abstract class BoundedCalendarRemote(protected val http: IntegrationHttpClient) : PersonalCalendarRemote {
    final override var remaining = 20; private set
    protected fun request(method: String,base: String,path: String = "",headers: Map<String,String> = emptyMap(),body: String? = null,contentType: String = "application/json"): IntegrationResponse {
        if (--remaining < 0) throw IntegrationUnavailable("Sync request limit reached. Remaining work will retry next cycle.")
        return http.scoped(method,base,path,headers,body,contentType,setOf(404,409,410,412))
    }
    protected fun version(value: String): String {
        if (value.isBlank() || value.length > 1024 || value.any { it.isISOControl() }) throw IntegrationUnavailable("Calendar provider did not return a safe event revision")
        return value
    }
}

@Component
class PersonalCalendarRemoteFactory(private val http: IntegrationHttpClient,private val mapper: ObjectMapper) {
    fun open(id: String,values: Map<String,String>): PersonalCalendarRemote {
        PersonalCalendarSchemas.validate(id,values)
        if (PersonalCalendarSchemas.missing(id,values).isNotEmpty()) throw IntegrationUnavailable("Complete your personal calendar credentials first")
        if (id == "calendar-caldav") return PersonalCalDavRemote(http,values)
        val microsoft = id == "calendar-microsoft"
        val url = if (microsoft) "https://login.microsoftonline.com/${values.getValue("tenantId")}/oauth2/v2.0/token" else "https://oauth2.googleapis.com/token"
        val form = mutableMapOf("client_id" to values.getValue("clientId"),"refresh_token" to values.getValue("refreshToken"),"grant_type" to "refresh_token")
        values["clientSecret"]?.let { form["client_secret"]=it }
        if (microsoft) form["scope"]="https://graph.microsoft.com/Calendars.ReadWrite offline_access"
        val response = http.request("POST",url,body=calendarForm(form),contentType="application/x-www-form-urlencoded")
        val token = accessToken(response)
        val updated = response.body?.path("refresh_token")?.asText()?.takeIf { it.isNotBlank() && it.length <= 4096 }
        return PersonalJsonCalendarRemote(http,mapper,id,values + (updated?.let { mapOf("refreshToken" to it) } ?: emptyMap()),token)
    }
}
