package com.starter.projects

import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*
import java.util.UUID

class ProjectReportAnalyticsTest {
    private val board=UUID.randomUUID();private val column=UUID.randomUUID()
    private fun ticket(days: Long,created: Boolean=true)=ProjectWorkItem(board,"Delivery",KanbanCard(UUID.randomUUID(),column,"Ticket",status=CardStatus.DONE,
        createdAt=if(created)Instant.parse("2026-10-01T10:00:00Z") else null,startDate=LocalDate.parse("2026-10-02"),completionDate=LocalDate.parse("2026-10-01").plusDays(days)))
    @Test fun `lead time excludes missing invalid and open records and keeps cycle time separate`() {
        val values=listOf(ticket(2),ticket(8),ticket(20,false),ticket(-1),ticket(6).let {it.copy(card=it.card.copy(status=CardStatus.IN_PROGRESS))})
        val metrics=ProjectReportAnalytics.leadTime(values)
        assertThat(metrics.averageDays).isEqualTo(5.0);assertThat(metrics.medianDays).isEqualTo(5.0);assertThat(metrics.sampleSize).isEqualTo(2)
        assertThat(metrics.completedTickets).isEqualTo(4);assertThat(metrics.missingCreation).isEqualTo(1);assertThat(metrics.invalidDates).isEqualTo(1)
        assertThat(metrics.distribution.sumOf {it.count}).isEqualTo(2);assertThat(metrics.cycleSampleSize).isEqualTo(3)
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
