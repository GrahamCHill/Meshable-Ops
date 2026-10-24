package com.starter.plugins.calendar

import com.fasterxml.jackson.databind.JsonNode
import com.starter.plugins.*
import com.starter.plugins.integrations.*
import com.starter.exception.BadRequestException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.*

internal fun encodeCalendar(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
internal fun calendarForm(values: Map<String,String>) = values.entries.joinToString("&") { "${encodeCalendar(it.key)}=${encodeCalendar(it.value)}" }
internal fun calendarValues(id: String, registry: PluginRegistry, settings: PluginConfigurationService): Map<String,String> {
    registry.requireAvailable(id, "ADMIN")
    val values = settings.current(id)
    if (registry.missingConfiguration(id, values).isNotEmpty()) throw BadRequestException("Complete this calendar connection's configuration first")
    registry.validateConfiguration(id, values)
    return values
}
internal fun accessToken(response: IntegrationResponse) = response.body?.path("access_token")?.asText()?.takeIf { it.isNotBlank() }
    ?: throw IntegrationUnavailable("Calendar provider did not return an access token")
internal fun remoteCalendarEvent(node: JsonNode, source: String, microsoft: Boolean): CalendarEventView? {
    if (node.path("status").asText() == "cancelled" || node.path("isCancelled").asBoolean()) return null
    val allDay = if (microsoft) node.path("isAllDay").asBoolean() else node.path("start").has("date")
    fun time(key: String): String {
        val obj = node.path(key)
        val value = obj.path(if (allDay && !microsoft) "date" else "dateTime").asText()
        return if (allDay) LocalDate.parse(value.take(10)).toString()
            else runCatching { OffsetDateTime.parse(value).toInstant().toString() }.getOrElse {
                // Microsoft calendarView is requested in UTC; never guess arbitrary provider time zones.
                if (microsoft && obj.path("timeZone").asText() == "UTC") LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toString()
                else throw IntegrationUnavailable("Calendar provider returned an unsupported event time")
            }
    }
    val id = node.path("id").asText().take(1000)
    if (id.isBlank()) throw IntegrationUnavailable("Calendar provider returned an event without an ID")
    return CalendarEventView("$source:$id", node.path(if (microsoft) "subject" else "summary").asText("Untitled event").take(300),
        node.path(if (microsoft) "bodyPreview" else "description").asText().take(4000), time("start"), time("end"), allDay, source)
}
