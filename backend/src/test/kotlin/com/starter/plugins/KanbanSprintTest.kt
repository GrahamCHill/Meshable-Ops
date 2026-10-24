package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.plugins.kanban.*
import com.starter.projects.ProjectBoardAccess
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class KanbanSprintTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val todo = KanbanColumn(UUID.randomUUID(), "To do", CardStatus.TODO)
    private val done = KanbanColumn(UUID.randomUUID(), "Done", CardStatus.DONE)
    private val sprint = KanbanSprint(UUID.randomUUID(), "Sprint 1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14))
    private val future = sprint.copy(id = UUID.randomUUID(), name = "Sprint 2")
    private val card = KanbanCard(UUID.randomUUID(), todo.id, "Parent", sprintId = sprint.id, reference = "OPS-1")
    private val content = KanbanContent(listOf(todo, done), listOf(card), listOf(sprint, future))

    @Test fun `legacy sprint JSON defaults to planned without losing dates`() {
        val json = """{"id":"${sprint.id}","name":"Legacy","startDate":"2026-10-01","endDate":"2026-10-14"}"""
        val restored = mapper.readValue(json, KanbanSprint::class.java)
        assertThat(restored.status).isEqualTo(SprintStatus.PLANNED)
        assertThat(restored.goal).isEmpty()
        assertThat(restored.startDate).isEqualTo(sprint.startDate)
    }

    @Test fun `a nonempty planned sprint can start but empty or parallel sprints cannot`() {
        val started = KanbanSprintRules.start(content, sprint.id)
        KanbanSprintRules.validate(started)
        KanbanSprintRules.check(content, started)
        assertThat(started.sprints.first().status).isEqualTo(SprintStatus.ACTIVE)
        assertThatThrownBy { KanbanSprintRules.check(content, KanbanSprintRules.start(content, future.id)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { KanbanSprintRules.validate(KanbanSprintRules.start(started, future.id)) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { KanbanSprintRules.start(started, sprint.id) }.isInstanceOf(BadRequestException::class.java)
    }

    @Test fun `completion carries an unfinished family including done subtasks to a future sprint`() {
        val child = card.copy(id = UUID.randomUUID(), type = TicketType.SUBTASK, parentId = card.id, columnId = done.id, status = CardStatus.DONE, reference = "OPS-2")
        val finished = card.copy(id = UUID.randomUUID(), columnId = done.id, status = CardStatus.DONE, reference = "OPS-3")
        val active = content.copy(sprints = listOf(sprint.copy(status = SprintStatus.ACTIVE), future), cards = listOf(card, child, finished))
        val completed = KanbanSprintRules.complete(active, sprint.id, future.id)
        KanbanSprintRules.check(active, completed)
        assertThat(completed.sprints.first().status).isEqualTo(SprintStatus.COMPLETED)
        assertThat(completed.cards.filter { it.sprintId == future.id }.map { it.id }).containsExactly(card.id, child.id)
        assertThat(completed.cards.last()).isEqualTo(finished)
        assertThat(completed.cards.map { it.reference }).containsExactly("OPS-1", "OPS-2", "OPS-3")
    }

    @Test fun `completion can return an unfinished family to the backlog`() {
        val child = card.copy(id = UUID.randomUUID(), type = TicketType.SUBTASK, parentId = card.id)
        val active = content.copy(sprints = listOf(sprint.copy(status = SprintStatus.ACTIVE)), cards = listOf(card, child))
        val completed = KanbanSprintRules.complete(active, sprint.id, null)
        assertThat(completed.cards).allMatch { it.sprintId == null }
        KanbanSprintRules.check(active, completed)
    }

    @Test fun `invalid completion destinations and unfinished children of done tickets are rejected`() {
        val active = content.copy(sprints = listOf(sprint.copy(status = SprintStatus.ACTIVE), future))
        assertThatThrownBy { KanbanSprintRules.complete(content, sprint.id, null) }.isInstanceOf(BadRequestException::class.java)
        listOf(sprint.id, UUID.randomUUID()).forEach { destination ->
            assertThatThrownBy { KanbanSprintRules.complete(active, sprint.id, destination) }.isInstanceOf(BadRequestException::class.java)
        }
        val finished = card.copy(status = CardStatus.DONE, columnId = done.id)
        val child = card.copy(id = UUID.randomUUID(), type = TicketType.SUBTASK, parentId = card.id)
        assertThatThrownBy { KanbanSprintRules.complete(active.copy(cards = listOf(finished, child)), sprint.id, null) }.isInstanceOf(BadRequestException::class.java)
    }

    @Test fun `full board writes cannot skip lifecycle or restart a completed sprint`() {
        val completed = content.copy(sprints = listOf(sprint.copy(status = SprintStatus.COMPLETED)))
        assertThatThrownBy { KanbanSprintRules.check(content, completed) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { KanbanSprintRules.check(completed, content) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { KanbanSprintRules.check(completed, completed.copy(sprints = emptyList())) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { KanbanSprintRules.validate(content.copy(sprints = listOf(sprint.copy(goal = "x".repeat(501))))) }.isInstanceOf(BadRequestException::class.java)
    }

    @Test fun `parent completion requires its subtasks to be done`() {
        val child = card.copy(id = UUID.randomUUID(), type = TicketType.SUBTASK, parentId = card.id)
        val previous = content.copy(cards = listOf(card, child))
        assertThatThrownBy { KanbanRules.check(UUID.randomUUID(), previous,
            previous.copy(cards = listOf(card.copy(status = CardStatus.DONE, columnId = done.id), child)), true) }.isInstanceOf(BadRequestException::class.java)
    }

    @Test fun `sprint API guards manager access and optimistic revision before writing`() {
        val boards = mock(KanbanRepository::class.java)
        val actors = mock(PluginActor::class.java)
        val plugins = mock(PluginRegistry::class.java)
        val access = mock(ProjectBoardAccess::class.java)
        val user = User(email = "member@example.test")
        val row = KanbanBoard(ownerId = user.id, name = "Board", contentJson = mapper.writeValueAsString(content), revision = 4)
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.locked(row.id)).thenReturn(Optional.of(row))
        `when`(access.readable(user, row)).thenReturn(true)
        val service = KanbanService(boards, actors, plugins, mapper, projects = access)
        assertThatThrownBy { service.startSprint(user.email, row.id, sprint.id, SprintActionRequest(4)) }.isInstanceOf(ForbiddenException::class.java)
        `when`(access.canManage(user, row)).thenReturn(true)
        assertThatThrownBy { service.startSprint(user.email, row.id, sprint.id, SprintActionRequest(3)) }.isInstanceOf(ConflictException::class.java)
        verify(boards, never()).saveAndFlush(any(KanbanBoard::class.java))
    }

    @Test fun `service persists lifecycle and makes subtasks inherit sprint on planning`() {
        val boards = mock(KanbanRepository::class.java)
        val actors = mock(PluginActor::class.java)
        val plugins = mock(PluginRegistry::class.java)
        val user = User(email = "owner@example.test")
        val child = card.copy(id = UUID.randomUUID(), type = TicketType.SUBTASK, parentId = card.id, sprintId = null)
        val row = KanbanBoard(ownerId = user.id, name = "Board", contentJson = mapper.writeValueAsString(content.copy(cards = listOf(card, child))), revision = 4)
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.locked(row.id)).thenReturn(Optional.of(row))
        `when`(boards.findById(row.id)).thenReturn(Optional.of(row))
        `when`(boards.saveAndFlush(row)).thenAnswer { row.revision++; row }
        val service = KanbanService(boards, actors, plugins, mapper)
        val started = service.startSprint(user.email, row.id, sprint.id, SprintActionRequest(4))
        assertThat(started.revision).isEqualTo(5)
        assertThat(started.content.cards).allMatch { it.sprintId == sprint.id }
        val finished = service.completeSprint(user.email, row.id, sprint.id, SprintActionRequest(5, future.id))
        assertThat(finished.revision).isEqualTo(6)
        assertThat(finished.content.cards).allMatch { it.sprintId == future.id }
        assertThat(finished.content.sprints.first().status).isEqualTo(SprintStatus.COMPLETED)
    }

    @Test fun `reopening completed sprint work returns its whole family to the backlog`() {
        val boards = mock(KanbanRepository::class.java)
        val actors = mock(PluginActor::class.java)
        val plugins = mock(PluginRegistry::class.java)
        val user = User(email = "owner@example.test")
        val finished = card.copy(columnId = done.id, status = CardStatus.DONE)
        val child = finished.copy(id = UUID.randomUUID(), type = TicketType.SUBTASK, parentId = card.id)
        val saved = content.copy(sprints = listOf(sprint.copy(status = SprintStatus.COMPLETED)), cards = listOf(finished, child))
        val row = KanbanBoard(ownerId = user.id, name = "Board", contentJson = mapper.writeValueAsString(saved))
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.findById(row.id)).thenReturn(Optional.of(row))
        `when`(boards.saveAndFlush(row)).thenReturn(row)
        val service = KanbanService(boards, actors, plugins, mapper)
        val reopened = service.save(user.email, row.id, KanbanRequest(row.name, saved.copy(cards = listOf(card, child)), row.revision))
        assertThat(reopened.content.cards).allMatch { it.sprintId == null }
        assertThat(reopened.content.cards.last().status).isEqualTo(CardStatus.DONE)
        assertThat(reopened.content.sprints.first().status).isEqualTo(SprintStatus.COMPLETED)
    }

    @Test fun `service cannot silently close unfinished work or assign new work to a completed sprint`() {
        val boards = mock(KanbanRepository::class.java)
        val actors = mock(PluginActor::class.java)
        val plugins = mock(PluginRegistry::class.java)
        val user = User(email = "owner@example.test")
        val active = content.copy(sprints = listOf(sprint.copy(status = SprintStatus.ACTIVE)))
        val row = KanbanBoard(ownerId = user.id, name = "Board", contentJson = mapper.writeValueAsString(active))
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.findById(row.id)).thenReturn(Optional.of(row))
        val service = KanbanService(boards, actors, plugins, mapper)
        val closed = active.copy(sprints = listOf(sprint.copy(status = SprintStatus.COMPLETED)))
        assertThatThrownBy { service.save(user.email, row.id, KanbanRequest(row.name, closed, row.revision)) }.isInstanceOf(BadRequestException::class.java)
        row.contentJson = mapper.writeValueAsString(closed.copy(cards = emptyList()))
        assertThatThrownBy { service.save(user.email, row.id, KanbanRequest(row.name, closed, row.revision)) }.isInstanceOf(BadRequestException::class.java)
        verify(boards, never()).saveAndFlush(any(KanbanBoard::class.java))
    }
}
