package com.starter.projects

import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ProjectWorkSummaryTest {
    @Test fun `project summary groups actual columns assignees epics and multiple tags`() {
        val person=UUID.randomUUID();val board=UUID.randomUUID();val column=UUID.randomUUID();val epic=UUID.randomUUID()
        val first=KanbanCard(UUID.randomUUID(),column,"Ticket",dueDate=LocalDate.of(2026,1,1),assigneeId=person,storyPoints=3.0,epicId=epic,tags=listOf("api","release"))
        val items=listOf(ProjectWorkItem(board,"Board",first,"Review","Launch"),ProjectWorkItem(board,"Board",first.copy(id=UUID.randomUUID(),status=CardStatus.DONE,assigneeId=null),"Done","Launch"))
        val summary=ProjectWorkService.summarize(items,mapOf(person.toString() to "Member"),LocalDate.of(2026,10,24))
        assertThat(summary.total).isEqualTo(2);assertThat(summary.completed).isEqualTo(1);assertThat(summary.overdue).isEqualTo(1)
        assertThat(summary.storyPoints).isEqualTo(6.0)
        assertThat(summary.stages.map { it.label }).containsExactlyInAnyOrder("Review","Done")
        assertThat(summary.assignees.map { it.label }).containsExactlyInAnyOrder("Member","Unassigned")
        assertThat(summary.epics.single().label).isEqualTo("Launch");assertThat(summary.epics.single().count).isEqualTo(2)
        assertThat(summary.tags.map { it.count }).containsExactly(2,2)
    }
}
