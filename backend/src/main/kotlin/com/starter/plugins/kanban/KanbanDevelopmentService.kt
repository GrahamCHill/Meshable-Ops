package com.starter.plugins.kanban

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.*
import com.starter.plugins.sourcecontrol.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class KanbanDevelopmentService(private val boards: KanbanRepository, private val links: KanbanDevelopmentLinkRepository,
    private val actors: PluginActor, private val registry: PluginRegistry, private val source: SourceControlService, private val mapper: ObjectMapper,
    private val projects: com.starter.projects.ProjectBoardAccess? = null,
    private val development: com.starter.projects.ProjectDevelopmentSettingsService? = null) {
    @Transactional(readOnly = true)
    fun list(email: String, boardId: UUID): List<BoardLinkView> {
        val actor = actor(email); val board=board(actor, boardId)
        val providers = registry.available(actor.role).filter { "source.repositories" in it.manifest.capabilities }.map { it.manifest.id }.toSet()
        return links.findByBoard_Id(boardId).filter { enabled(board,it.requestNumber) }.map { row -> view(row, row.providerId in providers) }
    }
    @Transactional
    fun add(email: String, boardId: UUID, request: BoardLinkRequest): BoardLinkView {
        val actor = actor(email, true); registry.requireFeature("kanban", "development-links", actor.role)
        registry.requireCapability("kanban", "source.repositories", request.providerId, actor.role)
        val board = board(actor, boardId, true)
        if(!enabled(board,request.number))throw ForbiddenException("Enable repository or pull request links in the project development settings first")
        if (request.repository.length > 200 || request.providerId !in setOf("github","gitlab","gitea")) throw BadRequestException("Choose a repository provider")
        val saved = links.findByBoard_Id(boardId)
        if (saved.size >= 100) throw BadRequestException("A board can have at most 100 development links")
        if (saved.any { it.providerId == request.providerId && it.repository.equals(request.repository,ignoreCase = request.providerId == "github") && it.requestNumber == request.number && it.cardId == request.cardId }) throw ConflictException("This resource is already linked")
        if (request.cardId != null && mapper.readValue(board.contentJson, KanbanContent::class.java).cards.none { it.id == request.cardId }) throw NotFoundException("Card unavailable")
        val remote = source.resource(request.providerId, request.repository, request.number, actor.role)
        recheck(email, actor, request.providerId)
        return view(links.saveAndFlush(KanbanDevelopmentLink(board = board, cardId = request.cardId, providerId = remote.providerId,
            repository = remote.repository, requestNumber = remote.number, title = remote.title, url = remote.url, state = remote.state)), true)
    }
    @Transactional
    fun refresh(email: String, boardId: UUID, linkId: UUID): BoardLinkView {
        val actor = actor(email, true); val board=board(actor, boardId)
        val row = links.findById(linkId).orElse(null)?.takeIf { it.board.id == boardId } ?: throw NotFoundException("Link unavailable")
        if(!enabled(board,row.requestNumber))throw ForbiddenException("Enable this development feature in the project settings first")
        registry.requireCapability("kanban", "source.repositories", row.providerId, actor.role)
        val remote = source.resource(row.providerId, row.repository, row.requestNumber, actor.role)
        recheck(email, actor, row.providerId)
        row.title = remote.title; row.url = remote.url; row.state = remote.state; row.checkedAt = Instant.now()
        return view(links.saveAndFlush(row), true)
    }
    @Transactional
    fun remove(email: String, boardId: UUID, linkId: UUID) {
        val actor = actor(email, true); board(actor, boardId)
        val row = links.findById(linkId).orElse(null)?.takeIf { it.board.id == boardId } ?: throw NotFoundException("Link unavailable")
        links.delete(row)
    }
    private fun actor(email: String, write: Boolean = false) = actors.current(email).also {
        registry.requireTable("kanban", "plugin_kanban_boards", it.role, write)
        registry.requireTable("kanban", "plugin_kanban_development_links", it.role, write)
    }
    private fun enabled(board:KanbanBoard,number:Int?)=board.projectId?.let {development?.effective(it)?.allows(if(number==null) "REPOSITORY" else "PULL_REQUEST")} ?: true
    private fun board(actor: User, id: UUID, lock: Boolean = false) = (if (lock) boards.locked(id) else boards.findById(id)).orElse(null)?.takeIf { projects?.readable(actor, it) ?: (it.ownerId == actor.id || com.starter.access.Roles.admin(actor.role)) } ?: throw NotFoundException("Board unavailable")
    private fun recheck(email: String, original: User, provider: String) {
        val current = actor(email, true)
        if (current.id != original.id || current.role != original.role) throw ForbiddenException("Access changed. Try again.")
        registry.requireCapability("kanban", "source.repositories", provider, current.role)
    }
    private fun view(row: KanbanDevelopmentLink, available: Boolean) = BoardLinkView(row.id, row.providerId, row.repository.takeIf { available }, row.requestNumber.takeIf { available }, row.cardId,
        if (available) row.title else "Supporting plugin unavailable", row.url.takeIf { available }, row.state.takeIf { available }, row.checkedAt.takeIf { available }, available)
}
