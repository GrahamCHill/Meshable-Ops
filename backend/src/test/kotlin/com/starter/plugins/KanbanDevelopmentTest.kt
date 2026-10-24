package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.kanban.*
import com.starter.plugins.sourcecontrol.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*

class KanbanDevelopmentTest {
    private val boards = mock(KanbanRepository::class.java)
    private val links = mock(KanbanDevelopmentLinkRepository::class.java)
    private val actors = mock(PluginActor::class.java)
    private val registry = mock(PluginRegistry::class.java)
    private val source = mock(SourceControlService::class.java)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val service = KanbanDevelopmentService(boards,links,actors,registry,source,mapper)
    private val user = User(email="member@example.test")
    private val board = KanbanBoard(ownerId=user.id)
    init {
        `when`(actors.current(user.email)).thenReturn(user)
        `when`(boards.findById(board.id)).thenReturn(Optional.of(board))
        `when`(boards.locked(board.id)).thenReturn(Optional.of(board))
    }
    @Test fun `other board owners cannot read add remove or refresh its links`() {
        val stranger = board.copyForTest()
        `when`(boards.findById(board.id)).thenReturn(Optional.of(stranger))
        `when`(boards.locked(board.id)).thenReturn(Optional.of(stranger))
        assertThatThrownBy { service.list(user.email,board.id) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.add(user.email,board.id,BoardLinkRequest("github","team/repo")) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.remove(user.email,board.id,UUID.randomUUID()) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.refresh(user.email,board.id,UUID.randomUUID()) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(source,links)
    }
    @Test fun `disabled provider metadata is redacted and local removal remains possible`() {
        val row = KanbanDevelopmentLink(board=board,providerId="github",repository="private/repo",title="Private title",url="https://github.com/private/repo")
        `when`(links.findByBoard_Id(board.id)).thenReturn(listOf(row))
        `when`(registry.available("USER")).thenReturn(emptyList())
        val view = service.list(user.email,board.id).single()
        assertThat(view.available).isFalse();assertThat(view.url).isNull();assertThat(view.repository).isNull();assertThat(view.title).isEqualTo("Supporting plugin unavailable")
        `when`(links.findById(row.id)).thenReturn(Optional.of(row))
        service.remove(user.email,board.id,row.id)
        verify(links).delete(row)
        verifyNoInteractions(source)
    }
    @Test fun `missing cards and duplicate links are rejected before provider requests`() {
        assertThatThrownBy { service.add(user.email,board.id,BoardLinkRequest("github","team/repo",cardId=UUID.randomUUID())) }.isInstanceOf(NotFoundException::class.java)
        `when`(links.findByBoard_Id(board.id)).thenReturn(listOf(KanbanDevelopmentLink(board=board,providerId="github",repository="team/repo")))
        assertThatThrownBy { service.add(user.email,board.id,BoardLinkRequest("github","team/repo")) }.isInstanceOf(ConflictException::class.java)
        verifyNoInteractions(source)
    }
    @Test fun `provider revocation or actor demotion after fetch prevents saving a link`() {
        `when`(source.resource("github","team/repo",null,"USER")).thenReturn(DevelopmentResource("github","team/repo",null,"Repo","https://github.com/team/repo","repository"))
        doNothing().doThrow(NotFoundException("Disabled")).`when`(registry).requireCapability("kanban","source.repositories","github","USER")
        assertThatThrownBy { service.add(user.email,board.id,BoardLinkRequest("github","team/repo")) }.isInstanceOf(NotFoundException::class.java)
        verify(links,never()).saveAndFlush(any(KanbanDevelopmentLink::class.java))
    }
    @Test fun `deleting a card removes its links while preserving board and remaining card links`() {
        val column = KanbanColumn(UUID.randomUUID(),"To do")
        val card = KanbanCard(UUID.randomUUID(),column.id,"Task",dueDate=java.time.LocalDate.parse("2026-10-24"))
        val stale = KanbanDevelopmentLink(board=board,cardId=UUID.randomUUID())
        val retained = KanbanDevelopmentLink(board=board,cardId=card.id)
        val boardLink = KanbanDevelopmentLink(board=board)
        `when`(boards.saveAndFlush(board)).thenReturn(board)
        `when`(links.findByBoard_Id(board.id)).thenReturn(listOf(stale,retained,boardLink))
        KanbanService(boards,actors,registry,mapper,development=links).save(user.email,board.id,KanbanRequest("Board",KanbanContent(listOf(column),listOf(card))))
        verify(links).deleteAll(listOf(stale))
    }
    private fun KanbanBoard.copyForTest() = KanbanBoard(id=id,ownerId=UUID.randomUUID())
}
