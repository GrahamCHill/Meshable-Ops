package com.starter.projects

import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*
import java.util.UUID

class ProjectReportAnalyticsTest {
    private val board=UUID.randomUUID();private val column=UUID.randomUUID()
    private fun ticket(days: Long,started: Boolean=true)=ProjectWorkItem(board,"Delivery",KanbanCard(UUID.randomUUID(),column,"Ticket",status=CardStatus.DONE,
        createdAt=Instant.parse("2026-09-01T10:00:00Z"),startDate=if(started)LocalDate.parse("2026-10-01") else null,completionDate=LocalDate.parse("2026-10-01").plusDays(days)))
    @Test fun `lead time uses start dates and excludes missing invalid and open records`() {
        val values=listOf(ticket(2),ticket(8),ticket(20,false),ticket(-1),ticket(6).let {it.copy(card=it.card.copy(status=CardStatus.IN_PROGRESS))},ticket(1).let {it.copy(card=it.card.copy(completionDate=null))})
        val metrics=ProjectReportAnalytics.leadTime(values)
        assertThat(metrics.averageDays).isEqualTo(5.0);assertThat(metrics.medianDays).isEqualTo(5.0);assertThat(metrics.sampleSize).isEqualTo(2)
        assertThat(metrics.completedTickets).isEqualTo(5);assertThat(metrics.missingStart).isEqualTo(1);assertThat(metrics.missingCompletion).isEqualTo(1);assertThat(metrics.invalidDates).isEqualTo(1)
        assertThat(metrics.distribution.sumOf {it.count}).isEqualTo(2);assertThat(metrics.boards.single().averageDays).isEqualTo(5.0)
        assertThat(ProjectReportAnalytics.leadTime(listOf(ticket(3).let {it.copy(card=it.card.copy(createdAt=null))})).averageDays).isEqualTo(3.0)
    }
    @Test fun `empty and same day lead times are distinguished`() {
        assertThat(ProjectReportAnalytics.leadTime(emptyList()).averageDays).isNull()
        assertThat(ProjectReportAnalytics.leadTime(listOf(ticket(0))).averageDays).isEqualTo(0.0)
    }
    @Test fun `every export field stays within its own length and line budget`() {
        ProjectReportAnalytics.cellLimits.forEach {limit->assertThat(ProjectReportAnalytics.excerpt("secret ".repeat(1000),limit).length).isLessThanOrEqualTo(limit)}
        assertThat(ProjectReportAnalytics.excerpt((1..100).joinToString("\n"),240).lines()).hasSizeLessThanOrEqualTo(8)
        assertThat(ProjectReportAnalytics.excerpt("normal description",240)).isEqualTo("normal description")
    }
}
