package com.starter.plugins.workflows

import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID

class KanbanWorkflowContextTest {
    @Test fun `bulk editing and the move endpoint resolve the same transition policy from saved state`() {
        val service=mock(KanbanService::class.java)
        val provider=KanbanWorkflowContext(service)
        val id=UUID.randomUUID();val from=UUID.randomUUID();val to=UUID.randomUUID();val ticketId=UUID.randomUUID()
        val content=KanbanContent(cards=listOf(KanbanCard(id=ticketId,columnId=from,title="Ticket")))
        `when`(service.get("member",id)).thenReturn(KanbanView(id,UUID.randomUUID(),"Board",Instant.now(),1,content))
        val bulk=provider.context("member","/api/plugins/kanban/boards/$id",KanbanRequest("Board",content.copy(cards=content.cards.map {it.copy(columnId=to)}),1))
        val move=provider.context("member","/api/plugins/kanban/boards/$id/cards/$ticketId/move",MoveTicketRequest(to,1))
        assertThat(bulk.selectors).contains("kanban.transition")
        assertThat(move.selectors).contains("kanban.transition")
        assertThat(bulk.values["trigger.kanban.targetColumnIds"]).isEqualTo(move.values["trigger.kanban.targetColumnIds"])
        verify(service,times(2)).get("member",id)
    }
}
