package com.starter.plugins.calendar

import com.starter.plugins.*
import com.starter.exception.*
import com.starter.plugins.integrations.IntegrationHttpClient
import java.util.UUID

object PersonalCalendarSchemas {
    private fun text(key: String,label: String,required: Boolean = true,help: String = "") = PluginSettingField(key,label,required=required,help=help,maxLength=2000)
    private fun secret(key: String,label: String,required: Boolean = true) = PluginSettingField(key,label,PluginFieldType.SECRET,required,maxLength=4096)
    val all = listOf(
        PersonalCalendarSchema("calendar-google","Google Calendar",listOf(text("clientId","OAuth client ID"),secret("clientSecret","OAuth client secret"),
            secret("refreshToken","Your offline refresh token"),text("calendarId","Calendar ID",help="Use primary or a calendar you own. Grant calendar.events read/write access."))),
        PersonalCalendarSchema("calendar-microsoft","Microsoft Calendar",listOf(text("tenantId","Tenant ID",help="Tenant UUID, common, consumers, or organizations."),text("clientId","OAuth client ID"),
            secret("clientSecret","OAuth client secret",false),secret("refreshToken","Your offline refresh token"),text("calendarId","Calendar ID",false,"Blank uses your default calendar. Uses delegated /me access with Calendars.ReadWrite and offline_access."))),
        PersonalCalendarSchema("calendar-caldav","CalDAV Calendar",listOf(text("calendarUrl","Your HTTPS calendar collection URL"),secret("username","Username"),secret("password","App password"))))
    fun schema(id: String) = all.find { it.id == id } ?: throw BadRequestException("Choose a supported personal calendar provider")
    fun validate(id: String,values: Map<String,String>) {
        if (id == "calendar-caldav") values["calendarUrl"]?.let { IntegrationHttpClient.endpoint(it) }
        if (id == "calendar-microsoft") {
            values["tenantId"]?.let { if (it !in setOf("common","consumers","organizations") && runCatching { UUID.fromString(it) }.isFailure) throw BadRequestException("Use a tenant UUID or a supported Microsoft tenant alias") }
            values["clientId"]?.let { if (runCatching { UUID.fromString(it) }.isFailure) throw BadRequestException("Use an OAuth client UUID") }
        }
    }
    fun missing(id: String,values: Map<String,String>) = schema(id).fields.filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
}
