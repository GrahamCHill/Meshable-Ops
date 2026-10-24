package com.starter.plugins.calendar

import com.starter.exception.*
import com.starter.plugins.*
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class CalendarService(private val events: CalendarEventRepository, private val actors: PluginActor, private val registry: PluginRegistry,
    private val jdbc: JdbcTemplate, private val projects: com.starter.projects.ProjectBoardAccess? = null) {
    fun window(email: String, from: Instant, until: Instant, zone: ZoneId = ZoneOffset.UTC): CalendarWindow {
        validateWindow(from, until)
        val actor = actor(email)
        val dateFrom=from.atZone(zone).toLocalDate()
        val dateUntil=until.atZone(zone).let { if (it.toLocalTime()==LocalTime.MIDNIGHT) it.toLocalDate() else it.toLocalDate().plusDays(1) }
        val local = events.window(actor.id, com.starter.access.Roles.admin(actor.role), from, until,dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),dateUntil.atStartOfDay(ZoneOffset.UTC).toInstant(),PageRequest.of(0,201))
        val deadlinePlugin = registry.available(actor.role).find { it.manifest.id == "kanban" }
        val due = if (deadlinePlugin?.tableAccess?.get("plugin_kanban_boards")?.readRoles?.contains(com.starter.access.Roles.plugin(actor.role)) == true) deadlines(actor, dateFrom, dateUntil) else emptyList()
        return CalendarWindow((local.take(200).map(::view) + due.take(200)).sortedBy { it.start }, local.size > 200 || due.size > 200)
    }
    @Transactional
    fun save(email: String, id: UUID?, request: CalendarEventRequest): CalendarEventView {
        val actor = actor(email, true)
        validateEvent(request)
        val row = if (id == null) CalendarEvent(ownerId = actor.id) else owned(email, id, true)
        if (id != null && row.revision != request.revision) throw ConflictException("This event changed. Reload before saving.")
        row.title = request.title.trim(); row.description = request.description; row.startsAt = request.start; row.endsAt = request.end; row.allDay = request.allDay
        row.syncExcluded = !request.syncEnabled
        row.detailsJson = CalendarEventDetails.write(CalendarEventDetails.read(row).copy(documents = request.documents, participants = request.participants))
        return view(events.saveAndFlush(row))
    }
    @Transactional
    fun delete(email: String, id: UUID, revision: Long) {
        val row = owned(email, id, true)
        if (row.revision != revision) throw ConflictException("This event changed. Reload before deleting.")
        row.deletedAt = Instant.now()
        events.saveAndFlush(row) // Retain a tombstone until personal sync can propagate the deletion.
    }
    fun owned(email: String, id: UUID, write: Boolean = false): CalendarEvent {
        val actor = actor(email, write)
        return events.findById(id).orElse(null)?.takeIf { it.deletedAt == null && (it.ownerId == actor.id || (com.starter.access.Roles.admin(actor.role) && !it.personal)) } ?: throw NotFoundException("Calendar event unavailable")
    }
    private fun actor(email: String, write: Boolean = false) = actors.current(email).also { registry.requireTable("calendar", "plugin_calendar_events", it.role, write) }
    private fun deadlines(actor: com.starter.model.postgres.User, from: LocalDate, until: LocalDate): List<CalendarEventView> {
        val predicate = if (projects == null) "(? = TRUE OR b.owner_id = ?)" else com.starter.projects.ProjectAccessSql.boardPredicate()
        val scope = projects?.sqlArgs(actor) ?: arrayOf(com.starter.access.Roles.admin(actor.role), actor.id)
        return jdbc.query("""
        SELECT b.id AS board_id,b.name,c->>'id' AS card_id,c->>'title' AS title,c->>'description' AS description,c->>'dueDate' AS due
        FROM plugin_kanban_boards b CROSS JOIN LATERAL jsonb_array_elements(CAST(b.content_json AS jsonb)->'cards') c
        WHERE $predicate AND c->>'dueDate' >= ? AND c->>'dueDate' < ? AND COALESCE(c->>'status', 'TODO') <> 'DONE' ORDER BY c->>'dueDate',b.id LIMIT 201
    """.trimIndent(), { rs, _ ->
        val date = LocalDate.parse(rs.getString("due")); val boardId = rs.getString("board_id")
        CalendarEventView("kanban:$boardId:${rs.getString("card_id")}", rs.getString("title"), rs.getString("name") + " · " + rs.getString("description").orEmpty(),
            date.toString(), date.plusDays(1).toString(), true, "kanban", "/plugins/kanban/boards?board=$boardId", calendarName = "Board: " + rs.getString("name"))
    }, *scope, from.toString(),until.toString())
    }
    companion object {
        fun validateWindow(from: Instant, until: Instant) { if (from < Instant.parse("1900-01-01T00:00:00Z") || until > Instant.parse("9999-12-31T23:59:59Z") || until <= from || Duration.between(from,until) > Duration.ofDays(93)) throw BadRequestException("Choose a calendar window of at most 93 days, between 1900 and 9999") }
        fun validateEvent(request: CalendarEventRequest) {
            CalendarEventDetails.validate(request.documents, request.participants)
            if (request.start < Instant.parse("1900-01-01T00:00:00Z") || request.end > Instant.parse("9999-12-31T23:59:59Z")) throw BadRequestException("Choose event dates between 1900 and 9999")
            if (request.title.isBlank() || request.title.length > 160 || request.description.length > 4000 || request.end <= request.start || Duration.between(request.start,request.end) > Duration.ofDays(366)) throw BadRequestException("Use a title, up to 4000 description characters, and a valid event duration")
            if (request.allDay && (request.start.atOffset(ZoneOffset.UTC).toLocalTime() != LocalTime.MIDNIGHT || request.end.atOffset(ZoneOffset.UTC).toLocalTime() != LocalTime.MIDNIGHT)) throw BadRequestException("All-day events use UTC date boundaries")
        }
        fun view(row: CalendarEvent): CalendarEventView { val details = CalendarEventDetails.read(row); return CalendarEventView(row.id.toString(),row.title,row.description,
            if (row.allDay) row.startsAt.atOffset(ZoneOffset.UTC).toLocalDate().toString() else row.startsAt.toString(),
            if (row.allDay) row.endsAt.atOffset(ZoneOffset.UTC).toLocalDate().toString() else row.endsAt.toString(),row.allDay,revision=row.revision,syncEnabled=!row.syncExcluded,
            documents=details.documents, participants=details.participants, calendarName=details.calendarName) }
    }
}
