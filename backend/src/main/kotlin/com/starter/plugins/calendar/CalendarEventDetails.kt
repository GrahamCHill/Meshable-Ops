package com.starter.plugins.calendar

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.starter.exception.BadRequestException
import java.net.URI

data class CalendarDocumentLink(val label: String, val url: String)
data class CalendarParticipant(val name: String = "", val email: String = "", val response: String = "")
data class CalendarEventExtras(val documents: List<CalendarDocumentLink> = emptyList(), val participants: List<CalendarParticipant> = emptyList(), val calendarName: String = "Local calendar")

/** References only: adding a link or participant never grants access, downloads a file, or sends an invitation. */
object CalendarEventDetails {
    private val mapper = jacksonObjectMapper()
    fun read(row: CalendarEvent): CalendarEventExtras = mapper.readValue(row.detailsJson)
    fun write(value: CalendarEventExtras): String = mapper.writeValueAsString(value)
    fun safeLink(value: String): Boolean = runCatching {
        if (value.length > 2048 || value.any { it.isWhitespace() || it.isISOControl() } || '\\' in value) return false
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null ||
            uri.scheme == null && uri.rawAuthority == null && uri.path in setOf("/storage", "/documents")
    }.getOrDefault(false)
    fun validate(documents: List<CalendarDocumentLink>, participants: List<CalendarParticipant>) {
        if (documents.size > 20 || participants.size > 100 || documents.any { it.label.isBlank() || it.label.length > 160 || it.label.any(Char::isISOControl) || !safeLink(it.url) } ||
            participants.any { it.name.length > 160 || it.name.any(Char::isISOControl) || it.email.length > 254 || it.name.isBlank() && it.email.isBlank() || it.email.isNotBlank() && !it.email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) || it.response !in setOf("", "accepted", "declined", "tentative", "needsAction", "none", "notResponded", "organizer") })
            throw BadRequestException("Use up to 20 labelled HTTPS/workspace document links and 100 participants with valid names or email addresses")
    }
    fun sourceName(source: String) = when(source) { "calendar-google" -> "Google calendar"; "calendar-microsoft" -> "Microsoft calendar"; "calendar-caldav" -> "CalDAV calendar"; "kanban" -> "Board deadlines"; else -> "Local calendar" }
}
