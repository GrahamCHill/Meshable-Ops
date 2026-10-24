package com.starter.projects

import com.starter.exception.BadRequestException
import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*
import java.util.UUID

class ProjectReportingTest {
    @Test fun `risk uses delivery facts and completed work never increases overdue risk`() {
        val today = LocalDate.of(2026, 10, 24); val id = UUID.randomUUID(); val column = UUID.randomUUID()
        val cards = listOf(KanbanCard(UUID.randomUUID(), column, "Overdue", dueDate = today.minusDays(1), blocked = true),
            KanbanCard(UUID.randomUUID(), column, "Soon", dueDate = today.plusDays(2)),
            KanbanCard(UUID.randomUUID(), column, "Done", dueDate = today.minusDays(30), status = CardStatus.DONE))
        val score = ProjectWorkService.score(id, "Member", cards, today)
        assertThat(score.score).isEqualTo(45)
        assertThat(score.overdue).isEqualTo(1); assertThat(score.blocked).isEqualTo(1); assertThat(score.dueSoon).isEqualTo(1)
        assertThat(ProjectWorkService.score(id, "Member", List(100) { cards.first() }, today).score).isEqualTo(100)
    }
    @Test fun `period matching includes open work and dated completion but excludes prior completed work`() {
        val from = LocalDate.of(2026, 10, 1); val to = LocalDate.of(2026, 10, 31)
        assertThat(ProjectReportService.inPeriod(from.minusDays(10), null, null, from, to)).isTrue()
        assertThat(ProjectReportService.inPeriod(null, null, from.plusDays(2), from, to)).isTrue()
        assertThat(ProjectReportService.inPeriod(from.minusDays(20), null, from.minusDays(1), from, to)).isFalse()
        assertThatThrownBy { ProjectReportService.validateWindow(to, from) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { ProjectReportService.validateWindow(from, from.plusDays(367)) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `schedules are disabled by default and compute timezone aware daily weekly and monthly runs`() {
        assertThat(WorkScheduleInput().enabled).isFalse()
        val weekly = WorkScheduleInput(cadence = WorkCadence.WEEKLY, dayOfWeek = 5, time = "00:00", timeZone = "Africa/Johannesburg")
        assertThat(WorkSchedules.next(weekly, Instant.parse("2026-10-22T23:00:00Z"))).isEqualTo(Instant.parse("2026-10-29T22:00:00Z"))
        val monthly = weekly.copy(cadence = WorkCadence.MONTHLY, dayOfMonth = 1)
        assertThat(WorkSchedules.next(monthly, Instant.parse("2026-10-24T12:00:00Z"))).isEqualTo(Instant.parse("2026-10-31T22:00:00Z"))
        assertThatThrownBy { WorkSchedules.next(weekly.copy(timeZone = "invalid"), Instant.now()) }.isInstanceOf(BadRequestException::class.java)
    }
}
