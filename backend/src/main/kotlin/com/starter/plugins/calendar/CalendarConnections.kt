package com.starter.plugins.calendar

import com.starter.plugins.*
import com.starter.exception.*
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

interface CalendarProvider {
    val pluginId: String
    fun events(from: Instant, until: Instant): CalendarWindow
    fun publish(event: CalendarEvent): CalendarPublication
}
data class CalendarPublication(val message: String = "Event published. Repeating this action uses the same remote event identity.")

@Service
class CalendarConnections(providers: List<CalendarProvider>, private val actors: PluginActor, private val registry: PluginRegistry,
    private val local: CalendarService) {
    private val providers = providers.associateBy { it.pluginId }
    fun window(email: String, id: String, from: Instant, until: Instant): CalendarWindow {
        CalendarService.validateWindow(from, until)
        val actor = check(email, id)
        val result = provider(id).events(from, until)
        val current = check(email, id)
        if (current.id != actor.id || current.role != actor.role) throw ForbiddenException("Access changed. Try again.")
        return result
    }
    fun publish(email: String, id: String, eventId: UUID): CalendarPublication {
        check(email, id)
        val event = local.owned(email, eventId, true)
        return provider(id).publish(event)
    }
    private fun check(email: String, id: String): com.starter.model.postgres.User {
        val actor = actors.current(email)
        if (actor.role != "ADMIN") throw ForbiddenException("Shared calendar connections are administrator-only. Use your personal connection.")
        registry.requireTable("calendar", "plugin_calendar_events", actor.role)
        registry.requireCapability("calendar", "calendar.provider", id, actor.role)
        return actor
    }
    private fun provider(id: String) = providers[id] ?: throw NotFoundException("Calendar connection unavailable")
}
