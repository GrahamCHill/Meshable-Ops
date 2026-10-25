package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.model.postgres.Event
import com.starter.repository.postgres.EventRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.Base64

class EventHistoryServiceTest {
    private val events = mock(EventRepository::class.java)
    private val mapper = jacksonObjectMapper()
    private val service = EventHistoryService(events, mapper)
    private val time = Instant.parse("2026-10-24T10:00:00Z")

    @Test fun `history uses one lookahead and the last returned row as its keyset`() {
        val rows = listOf("c", "b", "a").map { Event(id = it, type = "FOLDER_CREATED", createdAt = time) }
        `when`(events.history(any(Pageable::class.java))).thenAnswer {
            assertThat((it.arguments[0] as Pageable).pageSize).isEqualTo(3)
            rows
        }
        val first = service.history(null, 2)
        assertThat(first.content.map { it.id }).containsExactly("c", "b")
        `when`(events.historyBefore(eq(time), eq("b"), any(Pageable::class.java))).thenReturn(listOf(rows.last()))
        val second = service.history(first.nextCursor, 2)
        assertThat(second.content.map { it.id }).containsExactly("a")
        assertThat(second.nextCursor).isNull()
    }

    @Test fun `invalid cursors and sizes fail before reading history`() {
        listOf("", "not-a-cursor", Base64.getUrlEncoder().encodeToString("""{"createdAt":"bad","id":"x","kind":"STRING"}""".toByteArray()),
            Base64.getUrlEncoder().encodeToString(mapper.writeValueAsBytes(EventHistoryCursor(time.toString(), "abc", "OBJECT_ID")))).forEach {
            assertThatThrownBy { service.history(it, 10) }.isInstanceOf(BadRequestException::class.java)
        }
        listOf(0, 101).forEach { assertThatThrownBy { service.history(null, it) }.isInstanceOf(BadRequestException::class.java) }
        verifyNoInteractions(events)
    }
}
