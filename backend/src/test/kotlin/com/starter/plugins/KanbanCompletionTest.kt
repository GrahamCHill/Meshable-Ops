package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.User
import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.*
import java.util.*

class KanbanCompletionTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val boards=mock(KanbanRepository::class.java)
    private val actors=mock(PluginActor::class.java)
    private val user=User(email="owner@example.test")
    private val todo=KanbanColumn(UUID.randomUUID(),"To Do",CardStatus.TODO)
    private val done=KanbanColumn(UUID.randomUUID(),"Shipped",CardStatus.DONE)
    private val card=KanbanCard(UUID.randomUUID(),todo.id,"Ticket",createdAt=Instant.parse("2026-10-01T12:00:00Z"))
    private val board=KanbanBoard(ownerId=user.id,name="Delivery",contentJson=mapper.writeValueAsString(KanbanContent(listOf(todo,done),listOf(card))))
    private val service=KanbanService(boards,actors,mock(PluginRegistry::class.java),mapper)
    init {`when`(actors.current(user.email)).thenReturn(user);`when`(boards.findById(board.id)).thenReturn(Optional.of(board));`when`(boards.locked(board.id)).thenReturn(Optional.of(board));`when`(boards.saveAndFlush(board)).thenReturn(board)}
    @Test fun `direct moves complete tickets from the configured column and reopening clears completion`() {
        val completed=service.move(user.email,board.id,card.id,MoveTicketRequest(done.id,0)).content.cards.single()
        assertThat(completed.status).isEqualTo(CardStatus.DONE);assertThat(completed.completionDate).isEqualTo(LocalDate.now(ZoneOffset.UTC));assertThat(completed.createdAt).isEqualTo(card.createdAt)
        val reopened=service.move(user.email,board.id,card.id,MoveTicketRequest(todo.id,0)).content.cards.single()
        assertThat(reopened.status).isEqualTo(CardStatus.TODO);assertThat(reopened.completionDate).isNull()
    }
    @Test fun `full saves and legacy Done columns cannot leave tickets open`() {
        val legacy=done.copy(title="Done",status=null)
        val result=service.save(user.email,board.id,KanbanRequest(board.name,KanbanContent(listOf(todo,legacy),listOf(card.copy(columnId=done.id,status=CardStatus.TODO)))))
        assertThat(result.content.columns.last().status).isEqualTo(CardStatus.DONE)
        assertThat(result.content.cards.single().status).isEqualTo(CardStatus.DONE)
        assertThat(KanbanCompletion.status(legacy.copy(status=CardStatus.IN_PROGRESS))).isEqualTo(CardStatus.IN_PROGRESS)
    }
    @Test fun `new tickets get immutable creation timestamps while historical dates are not invented`() {
        val added=card.copy(id=UUID.randomUUID(),createdAt=Instant.parse("1900-01-01T00:00:00Z"))
        val result=service.save(user.email,board.id,KanbanRequest(board.name,KanbanContent(listOf(todo,done),listOf(card.copy(createdAt=Instant.now()),added))))
        assertThat(result.content.cards.first().createdAt).isEqualTo(card.createdAt)
        assertThat(result.content.cards.last().createdAt).isAfter(Instant.now().minusSeconds(10))
        val old=KanbanCompletion.read(KanbanContent(listOf(done.copy(title="Done",status=null)),listOf(card.copy(columnId=done.id,createdAt=null))))
        assertThat(old.cards.single().status).isEqualTo(CardStatus.DONE);assertThat(old.cards.single().completionDate).isNull();assertThat(old.cards.single().createdAt).isNull()
    }
}
