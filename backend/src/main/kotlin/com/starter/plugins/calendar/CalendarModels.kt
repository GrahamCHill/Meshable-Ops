package com.starter.plugins.calendar

import jakarta.persistence.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "plugin_calendar_events", indexes = [Index(columnList = "owner_id,starts_at")])
class CalendarEvent(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(), var title: String = "",
    @Column(columnDefinition = "TEXT") var description: String = "", var startsAt: Instant = Instant.now(), var endsAt: Instant = Instant.now(),
    var allDay: Boolean = false, @Version var revision: Long = 0,
    var deletedAt: Instant? = null, var originConnectionId: UUID? = null,
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("false") var syncExcluded: Boolean = false,
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("false") var personal: Boolean = false)
interface CalendarEventRepository : JpaRepository<CalendarEvent,UUID> {
    @Query("SELECT e FROM CalendarEvent e WHERE e.deletedAt IS NULL AND (e.ownerId = :owner OR (:admin = true AND e.personal = false)) AND ((e.allDay = false AND e.startsAt < :until AND e.endsAt > :from) OR (e.allDay = true AND e.startsAt < :dateUntil AND e.endsAt > :dateFrom)) ORDER BY e.startsAt")
    fun window(owner: UUID, admin: Boolean, from: Instant, until: Instant, dateFrom: Instant, dateUntil: Instant, pageable: Pageable): List<CalendarEvent>
    @Query("SELECT e FROM CalendarEvent e WHERE e.ownerId = :owner AND e.startsAt < :until AND e.endsAt > :from ORDER BY e.startsAt")
    fun forSync(owner: UUID, from: Instant, until: Instant, pageable: Pageable): List<CalendarEvent>
    fun findAllByOriginConnectionId(id: UUID): List<CalendarEvent>
}
data class CalendarEventRequest(val title: String, val description: String = "", val start: Instant, val end: Instant, val allDay: Boolean = false, val revision: Long = 0, val syncEnabled: Boolean = true)
data class CalendarEventView(val id: String, val title: String, val description: String, val start: String, val end: String, val allDay: Boolean,
    val source: String = "calendar", val href: String? = null, val revision: Long = 0, val syncEnabled: Boolean = true)
data class CalendarWindow(val events: List<CalendarEventView>, val truncated: Boolean = false, val notice: String? = null)
