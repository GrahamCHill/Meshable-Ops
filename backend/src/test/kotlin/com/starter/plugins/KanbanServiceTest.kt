package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*

class KanbanServiceTest {
    private val boards = mock(KanbanRepository::class.java)
    private val actors = mock(PluginActor::class.java)
    private val plugins = mock(PluginRegistry::class.java)
    private val service = KanbanService(boards, actors, plugins, jacksonObjectMapper())
    private val user = User(email = "user@example.test")
    private val column = KanbanColumn(UUID.randomUUID(), "To do")
    private val content = KanbanContent(listOf(column))

    @Test fun `ordinary members cannot create boards even when the plugin table is writable`() {
        `when`(actors.current(user.email)).thenReturn(user)
        assertThat(service.creationPermissions(user.email, null)["canCreate"]).isFalse()
        assertThatThrownBy { service.save(user.email, null, KanbanRequest("Board", content)) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(boards)
    }
    @Test fun `Business Managers can create private boards and retain ownership checks`() {
        val manager = user.copy(role = com.starter.access.Roles.BUSINESS_MANAGER)
        `when`(actors.current(user.email)).thenReturn(manager)
        `when`(boards.saveAndFlush(any(KanbanBoard::class.java))).thenAnswer { it.arguments[0] }
        assertThat(service.creationPermissions(user.email, null)["canCreate"]).isTrue()
        val created = service.save(user.email, null, KanbanRequest("Business board", content))
        assertThat(created.ownerId).isEqualTo(manager.id)
        assertThat(created.canManage).isTrue()
    }

    @Test fun `other owners cannot read or edit a board while admins can`() {
        val row = KanbanBoard(ownerId = UUID.randomUUID(), contentJson = "{\"columns\":[],\"cards\":[]}")
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.findById(row.id)).thenReturn(Optional.of(row))
        assertThatThrownBy { service.get(user.email, row.id) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.save(user.email, row.id, KanbanRequest("Board", content)) }.isInstanceOf(NotFoundException::class.java)
        `when`(actors.current(user.email)).thenReturn(user.copy(role = "ADMIN"))
        assertThat(service.get(user.email, row.id).id).isEqualTo(row.id)
    }
    @Test fun `disabled plugin cannot be used through a service call`() {
        `when`(actors.current(user.email)).thenReturn(user)
        doThrow(NotFoundException("Plugin unavailable")).`when`(plugins).requireTable("kanban", "plugin_kanban_boards", "USER", false)
        assertThatThrownBy { service.list(user.email) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(boards)
    }
    @Test fun `stale saves fail without replacing board content`() {
        val row = KanbanBoard(ownerId = user.id, revision = 4)
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.findById(row.id)).thenReturn(Optional.of(row))
        assertThatThrownBy { service.save(user.email, row.id, KanbanRequest("Board", content, 3)) }.isInstanceOf(ConflictException::class.java)
        verify(boards, never()).saveAndFlush(any(KanbanBoard::class.java))
    }
    @Test fun `dangling card references duplicate IDs and oversized content are rejected`() {
        service.validate(KanbanRequest("Board", content))
        val card = KanbanCard(UUID.randomUUID(), column.id, "Task")
        listOf(
            KanbanRequest("", content), KanbanRequest("Board", KanbanContent()),
            KanbanRequest("Board", KanbanContent(listOf(column, column))),
            KanbanRequest("Board", content.copy(cards = listOf(card, card))),
            KanbanRequest("Board", content.copy(cards = listOf(card.copy(columnId = UUID.randomUUID())))),
            KanbanRequest("Board", content.copy(cards = listOf(card.copy(description = "x".repeat(2001))))),
            KanbanRequest("Board", content.copy(cards = List(201) { card.copy(id = UUID.randomUUID()) }))
        ).forEach { invalid -> assertThatThrownBy { service.validate(invalid) }.isInstanceOf(BadRequestException::class.java) }
    }
}
