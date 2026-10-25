package com.starter.projects

import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*
import java.util.UUID

class ProjectSprintMonitorTest {
    private val board=UUID.randomUUID()
    private val sprint=KanbanSprint(UUID.randomUUID(),"Sprint 1",LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-14"),SprintStatus.ACTIVE)
    private val today=LocalDate.parse("2026-10-10")
    private fun ticket(done: String?=null)=ProjectWorkItem(board,"Delivery",KanbanCard(UUID.randomUUID(),UUID.randomUUID(),"Ticket",sprintId=sprint.id,
        status=if(done==null)CardStatus.IN_PROGRESS else CardStatus.DONE,completionDate=done?.let {LocalDate.parse(it)},reference="OPS-1"),sprint=sprint)
    @Test fun `sprint duration measures from sprint start and distinguishes completed durations from live ages`() {
        val value=ProjectSprintMonitor.summarize(listOf(ticket("2026-10-04"),ticket("2026-10-17"),ticket()),today)
        assertThat(value.completedAverageDays).isEqualTo(9.5);assertThat(value.completedSampleSize).isEqualTo(2)
        assertThat(value.openAverageDays).isEqualTo(9.0);assertThat(value.oldestOpenDays).isEqualTo(9)
        assertThat(value.finishedAfterEnd).isEqualTo(1);assertThat(value.completionPercent).isEqualTo(200.0/3)
        assertThat(value.sprints.single().completedSampleSize).isEqualTo(2);assertThat(value.longestTickets.first().days).isEqualTo(16)
    }
    @Test fun `unknown future and incomplete sprint dates are counted instead of inventing durations`() {
        val missing=ticket().let {it.copy(card=it.card.copy(status=CardStatus.DONE))}
        val values=listOf(ticket().let {it.copy(card=it.card.copy(sprintId=null),sprint=null)},ticket().copy(sprint=null),missing,ticket("2026-09-30"),ticket().copy(sprint=sprint.copy(startDate=today.plusDays(2))))
        val value=ProjectSprintMonitor.summarize(values,today)
        assertThat(value.noSprint).isEqualTo(1);assertThat(value.missingSprint).isEqualTo(1);assertThat(value.missingCompletion).isEqualTo(1)
        assertThat(value.invalidDates).isEqualTo(1);assertThat(value.notStarted).isEqualTo(1)
        assertThat(value.completedAverageDays).isNull();assertThat(value.openAverageDays).isNull();assertThat(value.longestTickets).isEmpty()
    }
    @Test fun `ticket export provides the dates behind all three duration measures`() {
        val item=ticket("2026-10-04").let {it.copy(card=it.card.copy(startDate=LocalDate.parse("2026-10-03"),createdAt=Instant.parse("2026-10-01T10:00:00Z")))}
        val dates=ProjectReportAnalytics.dates(item,today)
        assertThat(dates).contains("Created: 2026-10-01","Start: 2026-10-03","Done: 2026-10-04","Lead: 1d","Created-done: 3d","Sprint start: 2026-10-01","Sprint done: 3d")
        assertThat(dates.lines()).hasSize(8);assertThat(dates.length).isLessThanOrEqualTo(ProjectReportAnalytics.cellLimits[3])
    }
    @Test fun `monitor summaries are bounded while reporting the full sprint count`() {
        val items=(1..9).map {ticket().copy(sprint=sprint.copy(id=UUID.randomUUID())).let {it.copy(card=it.card.copy(sprintId=it.sprint!!.id))}}
        val value=ProjectSprintMonitor.summarize(items,today)
        assertThat(value.assignedTickets).isEqualTo(9);assertThat(value.totalSprints).isEqualTo(9)
        assertThat(value.sprints).hasSize(6);assertThat(value.longestTickets).hasSize(6)
    }
}
