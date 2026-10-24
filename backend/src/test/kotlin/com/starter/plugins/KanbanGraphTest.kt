package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class KanbanGraphTest {
    private val a=KanbanColumn(UUID.randomUUID(),"To do",CardStatus.TODO)
    private val b=KanbanColumn(UUID.randomUUID(),"Review",CardStatus.IN_PROGRESS)
    private val c=KanbanColumn(UUID.randomUUID(),"Done",CardStatus.DONE)
    private val actor=UUID.randomUUID()
    private val card=KanbanCard(UUID.randomUUID(),a.id,"Task",assigneeId=actor)
    private val edge=KanbanTransition(UUID.randomUUID(),a.id,b.id,"Request review",ColumnPermission(MovePermission.ASSIGNEE))
    private val graph=KanbanWorkflow(true,listOf(edge))
    private val content=KanbanContent(listOf(a,b,c),listOf(card),workflow=graph)
    @Test fun `legacy boards retain free movement while graph paths and role restrictions apply when enabled`() {
        assertThat(KanbanRules.canMove(actor,card,a,c,false)).isTrue()
        assertThat(KanbanRules.canMove(actor,card,a,b,false,graph)).isTrue()
        assertThat(KanbanRules.canMove(UUID.randomUUID(),card,a,b,false,graph)).isFalse()
        assertThat(KanbanRules.canMove(actor,card,a,c,true,graph)).isFalse()
        assertThat(KanbanRules.canMove(actor,card,a,a,false,graph)).isTrue()
        assertThat(KanbanRules.canMove(actor,card,b,a,false,graph)).isFalse()
    }
    @Test fun `full board edits use the saved workflow and identity before a movement`() {
        assertThatThrownBy { KanbanRules.check(actor,content,content.copy(workflow=KanbanWorkflow(),cards=listOf(card.copy(columnId=c.id,status=CardStatus.DONE))),true) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { KanbanRules.check(actor,content,content.copy(workflow=graph.copy(enabled=false)),false) }.isInstanceOf(ForbiddenException::class.java)
        val stranger=UUID.randomUUID()
        assertThatThrownBy { KanbanRules.check(stranger,content,content.copy(cards=listOf(card.copy(columnId=b.id,status=CardStatus.IN_PROGRESS,assigneeId=stranger))),false) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `invalid graph references duplicate paths and nonfinite positions are rejected`() {
        listOf(graph.copy(transitions=listOf(edge,edge.copy(id=UUID.randomUUID()))),graph.copy(transitions=listOf(edge.copy(target=UUID.randomUUID()))),
            graph.copy(positions=mapOf(a.id to WorkflowPosition(Double.NaN,1.0)))).forEach { workflow ->
            assertThatThrownBy { KanbanGraphRules.validate(content.copy(workflow=workflow)) }.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `related tickets allow reciprocal links but blockers cannot form a cycle`() {
        val second=card.copy(id=UUID.randomUUID(),title="Second")
        KanbanGraphRules.validate(content.copy(cards=listOf(card.copy(links=listOf(TicketLink(second.id))),second.copy(links=listOf(TicketLink(card.id))))))
        val blocked=card.copy(links=listOf(TicketLink(second.id,TicketLinkType.BLOCKED_BY)))
        KanbanGraphRules.validate(content.copy(cards=listOf(blocked,second.copy(links=listOf(TicketLink(card.id,TicketLinkType.BLOCKS))))))
        assertThatThrownBy { KanbanGraphRules.validate(content.copy(cards=listOf(blocked,second.copy(links=listOf(TicketLink(card.id,TicketLinkType.BLOCKED_BY)))))) }.isInstanceOf(BadRequestException::class.java)
        listOf(card.id,UUID.randomUUID()).forEach { target ->
            val invalid=content.copy(cards=listOf(card.copy(links=listOf(TicketLink(target)))))
            assertThatThrownBy { KanbanGraphRules.validate(invalid) }.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `old JSON gains safe graph link and estimate defaults`() {
        val mapper=jacksonObjectMapper().findAndRegisterModules()
        val legacy=mapper.readValue("""{"columns":[],"cards":[],"sprints":[]}""",KanbanContent::class.java)
        assertThat(legacy.workflow.enabled).isFalse()
        assertThat(mapper.readValue("""{"id":"${card.id}","columnId":"${a.id}","title":"Old"}""",KanbanCard::class.java).links).isEmpty()
    }
}
