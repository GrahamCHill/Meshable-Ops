package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.kanban.*
import com.starter.projects.ProjectBoardAccess
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import java.util.UUID

class KanbanWorkflowTest {
    private val actor = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val todo = KanbanColumn(UUID.randomUUID(), "To do", CardStatus.TODO)
    private val review = KanbanColumn(UUID.randomUUID(), "Review", CardStatus.IN_PROGRESS, enter = ColumnPermission(MovePermission.REVIEWERS))
    private val card = KanbanCard(UUID.randomUUID(), todo.id, "Work", assigneeId = other)
    private val content = KanbanContent(listOf(todo,review),listOf(card))

    @Test fun `both departure and destination rules apply with a manager override`() {
        assertThat(KanbanRules.canMove(actor,card,todo,review,false)).isFalse()
        assertThat(KanbanRules.canMove(actor,card.copy(reviewerIds=setOf(actor)),todo,review,false)).isTrue()
        val locked = todo.copy(leave=ColumnPermission(MovePermission.MANAGERS))
        assertThat(KanbanRules.canMove(actor,card.copy(reviewerIds=setOf(actor)),locked,review,false)).isFalse()
        assertThat(KanbanRules.canMove(actor,card,locked,review,true)).isTrue()
        assertThat(KanbanRules.allows(ColumnPermission(MovePermission.SELECTED_MEMBERS,setOf(actor)),actor,card,false)).isTrue()
        assertThat(KanbanRules.allows(ColumnPermission(MovePermission.SELECTED_MEMBERS),actor,card,false)).isFalse()
    }
    @Test fun `reassigning or changing reviewers in the same request cannot grant a transition`() {
        val next=content.copy(cards=listOf(card.copy(columnId=review.id,reviewerIds=setOf(actor))))
        assertThatThrownBy { KanbanRules.check(actor,content,next,false) }.isInstanceOf(ForbiddenException::class.java)
        val assigned=review.copy(enter=ColumnPermission(MovePermission.ASSIGNEE))
        assertThatThrownBy { KanbanRules.check(actor,content.copy(columns=listOf(todo,assigned)),next.copy(columns=listOf(todo,assigned),cards=listOf(next.cards.single().copy(assigneeId=actor))),false) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `only managers can change columns permissions sprints and epics`() {
        listOf(content.copy(columns=content.columns.reversed()),content.copy(columns=listOf(todo,review.copy(enter=ColumnPermission()))),
            content.copy(epics=listOf(KanbanEpic(UUID.randomUUID(),"Release")))).forEach { changed ->
            assertThatThrownBy { KanbanRules.check(actor,content,changed,false) }.isInstanceOf(ForbiddenException::class.java)
            KanbanRules.check(actor,content,changed,true)
        }
    }
    @Test fun `deleting a ticket cannot bypass the departure restriction`() {
        val locked=content.copy(columns=listOf(todo.copy(leave=ColumnPermission(MovePermission.MANAGERS)),review))
        assertThatThrownBy { KanbanRules.check(actor,locked,locked.copy(cards=emptyList()),false) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `epics tags and subtask links validate without changing legacy content`() {
        KanbanRules.validate(content)
        val epic=KanbanEpic(UUID.randomUUID(),"Launch")
        KanbanRules.validate(content.copy(epics=listOf(epic),cards=listOf(card.copy(tags=listOf("design","api"),epicId=epic.id),
            card.copy(id=UUID.randomUUID(),type=TicketType.SUBTASK,parentId=card.id))))
        listOf(content.copy(cards=listOf(card.copy(epicId=UUID.randomUUID()))),content.copy(cards=listOf(card.copy(tags=listOf("duplicate","duplicate")))) ,
            content.copy(cards=listOf(card.copy(type=TicketType.SUBTASK,parentId=card.id))),content.copy(cards=listOf(card.copy(type=TicketType.SUBTASK))),
            content.copy(epics=listOf(epic.copy(color="red")))).forEach { bad -> assertThatThrownBy { KanbanRules.validate(bad) }.isInstanceOf(BadRequestException::class.java) }
    }
    @Test fun `full board writes and the dedicated move API enforce the same transition policy`() {
        val boards=mock(KanbanRepository::class.java);val actors=mock(PluginActor::class.java);val plugins=mock(PluginRegistry::class.java);val projects=mock(ProjectBoardAccess::class.java)
        val user=User(id=actor,email="member@example.test")
        val row=KanbanBoard(ownerId=other,name="Board",contentJson=jacksonObjectMapper().writeValueAsString(content),projectId=UUID.randomUUID(),revision=3)
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.findById(row.id)).thenReturn(Optional.of(row));`when`(boards.locked(row.id)).thenReturn(Optional.of(row))
        `when`(projects.readable(user,row)).thenReturn(true)
        val service=KanbanService(boards,actors,plugins,jacksonObjectMapper(),projects=projects)
        assertThatThrownBy { service.save(user.email,row.id,KanbanRequest("Board",content.copy(cards=listOf(card.copy(columnId=review.id))),3,row.projectId)) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.move(user.email,row.id,card.id,MoveTicketRequest(review.id,3)) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.move(user.email,row.id,card.id,MoveTicketRequest(review.id,2)) }.isInstanceOf(ConflictException::class.java)
        verify(boards,never()).saveAndFlush(any(KanbanBoard::class.java))
    }
    @Test fun `a permitted move persists rank reporting status completion and the existing ticket reference`() {
        val boards=mock(KanbanRepository::class.java);val actors=mock(PluginActor::class.java);val plugins=mock(PluginRegistry::class.java)
        val user=User(id=actor,email="owner@example.test");val done=review.copy(status=CardStatus.DONE)
        val savedCard=card.copy(reference="OPS-12");val second=card.copy(id=UUID.randomUUID(),columnId=done.id,reference="OPS-13")
        val json=jacksonObjectMapper().findAndRegisterModules();val row=KanbanBoard(ownerId=actor,name="Board",contentJson=json.writeValueAsString(content.copy(columns=listOf(todo,done),cards=listOf(savedCard,second))),revision=3)
        `when`(actors.current(user.email)).thenReturn(user);`when`(boards.locked(row.id)).thenReturn(Optional.of(row));`when`(boards.findById(row.id)).thenReturn(Optional.of(row))
        `when`(boards.saveAndFlush(row)).thenAnswer { row.revision++;row }
        val result=KanbanService(boards,actors,plugins,json).move(user.email,row.id,card.id,MoveTicketRequest(done.id,3,second.id))
        assertThat(result.content.cards.filter { it.columnId==done.id }.map { it.id }).containsExactly(card.id,second.id)
        val moved=result.content.cards.first { it.id==card.id }
        assertThat(moved.status).isEqualTo(CardStatus.DONE);assertThat(moved.completionDate).isNotNull();assertThat(moved.reference).isEqualTo("OPS-12")
        assertThat(result.revision).isEqualTo(4)
    }
}
