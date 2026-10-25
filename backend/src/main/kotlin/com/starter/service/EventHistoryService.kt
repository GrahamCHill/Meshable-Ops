package com.starter.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.EventResponse
import com.starter.exception.BadRequestException
import com.starter.repository.postgres.EventRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

data class EventHistoryPage(val content: List<EventResponse>, val nextCursor: String?)
internal data class EventHistoryCursor(val createdAt: String, val id: String, val kind: String = "STRING")

/** Timestamp and identifier form a stable keyset, including imported history IDs. */
@Service
class EventHistoryService(private val events: EventRepository, private val json: ObjectMapper) {
    fun history(cursor: String?, size: Int): EventHistoryPage {
        if (size !in 1..100) throw BadRequestException("Choose an event page size between 1 and 100")
        val paging = PageRequest.of(0, size + 1)
        val rows = if (cursor == null) events.history(paging) else {
            val decoded = decode(cursor)
            events.historyBefore(Instant.parse(decoded.createdAt), decoded.id, paging)
        }
        val page = rows.take(size)
        val next = if (rows.size > size) page.last().let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(EventHistoryCursor(it.createdAt.toString(), it.id)))
        } else null
        return EventHistoryPage(page.map { EventResponse(it.id, it.type, it.payload, it.createdAt.toString()) }, next)
    }

    private fun decode(value: String): EventHistoryCursor = try {
        require(value.length in 1..2048)
        val cursor = json.readValue(Base64.getUrlDecoder().decode(value), EventHistoryCursor::class.java)
        require(cursor.id.length in 1..512 && cursor.kind == "STRING")
        Instant.parse(cursor.createdAt)
        cursor
    } catch (_: Exception) { throw BadRequestException("Invalid event cursor. Refresh the event history.") }
}
