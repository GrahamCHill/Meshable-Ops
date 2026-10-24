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
    @Test fun `entering a work started column dates a ticket once and preserves earlier start dates`() {
        val started=KanbanColumn(UUID.randomUUID(),"Development",CardStatus.IN_PROGRESS,startsWork=true)
        val review=KanbanColumn(UUID.randomUUID(),"Review",CardStatus.IN_PROGRESS,startsWork=false)
        board.contentJson=mapper.writeValueAsString(KanbanContent(listOf(todo,started,review,done),listOf(card.copy(dueDate=LocalDate.now(ZoneOffset.UTC).minusDays(2)))))
        val moved=service.move(user.email,board.id,card.id,MoveTicketRequest(started.id,0)).content.cards.single()
        assertThat(moved.startDate).isEqualTo(LocalDate.now(ZoneOffset.UTC))
        val recorded=LocalDate.parse("2026-10-01")
        board.contentJson=mapper.writeValueAsString(KanbanContent(listOf(todo,started,review,done),listOf(card.copy(startDate=recorded))))
        val again=service.move(user.email,board.id,card.id,MoveTicketRequest(started.id,0)).content.cards.single()
        assertThat(again.startDate).isEqualTo(recorded)
        service.move(user.email,board.id,card.id,MoveTicketRequest(todo.id,0))
        assertThat(service.move(user.email,board.id,card.id,MoveTicketRequest(started.id,0)).content.cards.single().startDate).isEqualTo(recorded)
    }
    @Test fun `full saves date new work entries but unrelated saves and ordinary in progress columns do not invent dates`() {
        val started=KanbanColumn(UUID.randomUUID(),"Development",CardStatus.IN_PROGRESS,startsWork=true)
        val review=KanbanColumn(UUID.randomUUID(),"Review",CardStatus.IN_PROGRESS,startsWork=false)
        val columns=listOf(todo,started,review,done)
        val moved=service.save(user.email,board.id,KanbanRequest(board.name,KanbanContent(columns,listOf(card.copy(columnId=started.id)))))
        assertThat(moved.content.cards.single().startDate).isEqualTo(LocalDate.now(ZoneOffset.UTC))
        val undated=card.copy(columnId=started.id,status=CardStatus.IN_PROGRESS)
        board.contentJson=mapper.writeValueAsString(KanbanContent(columns,listOf(undated)))
        val edited=service.save(user.email,board.id,KanbanRequest(board.name,KanbanContent(columns,listOf(undated.copy(title="Edited")))))
        assertThat(edited.content.cards.single().startDate).isNull()
        assertThat(service.move(user.email,board.id,card.id,MoveTicketRequest(review.id,0)).content.cards.single().startDate).isNull()
        val added=card.copy(id=UUID.randomUUID(),columnId=started.id)
        val current=service.get(user.email,board.id).content
        assertThat(service.save(user.email,board.id,KanbanRequest(board.name,current.copy(cards=current.cards+added))).content.cards.last().startDate).isEqualTo(LocalDate.now(ZoneOffset.UTC))
        assertThat(KanbanCompletion.read(KanbanContent(listOf(started.copy(title="In progress",startsWork=null)))).columns.single().startsWork).isTrue()
        assertThat(KanbanCompletion.read(KanbanContent(listOf(started.copy(title="In progress",startsWork=false)))).columns.single().startsWork).isFalse()
    }
}
