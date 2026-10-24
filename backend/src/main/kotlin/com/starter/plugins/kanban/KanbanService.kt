package com.starter.plugins.kanban

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import com.starter.plugins.PluginRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class KanbanService(private val boards: KanbanRepository, private val actors: PluginActor,
    private val plugins: PluginRegistry, private val mapper: ObjectMapper,
    private val search: KanbanSearchProvider? = null, private val development: KanbanDevelopmentLinkRepository? = null,
    private val projects: com.starter.projects.ProjectBoardAccess? = null, private val comments: KanbanCommentRepository? = null) {
    fun list(email: String): List<KanbanSummary> { val actor = actor(email); return projects?.list(actor) ?: boards.visible(actor.id, com.starter.access.Roles.admin(actor.role), PageRequest.of(0, 100)) }
    fun get(email: String, id: UUID) = view(owned(actor(email), id))
    fun members(email: String, id: UUID) = actor(email).let { actor -> projects?.members(actor, owned(actor, id)) ?: emptyList() }
    @Transactional
    fun save(email: String, id: UUID?, request: KanbanRequest): KanbanView {
        val actor = actor(email)
        plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true)
        validate(request)
        val row = id?.let { owned(actor, it) } ?: KanbanBoard(ownerId = actor.id)
        if (id != null && row.revision != request.revision) throw ConflictException("This board changed. Reload before editing.")
        projects?.attach(actor, row, request.projectId)
        val previous = if (id == null) KanbanContent() else mapper.readValue(row.contentJson, KanbanContent::class.java)
        if (previous.sprints != request.content.sprints) projects?.requireManager(actor, row)
        row.projectId = request.projectId
        projects?.validatePeople(actor, row, request.content.cards.flatMap { it.reviewerIds + listOfNotNull(it.assigneeId) }.toSet())
        val old = previous.cards.associateBy { it.id }
        val content = request.content.copy(cards = request.content.cards.map { card -> card.copy(
            reference = old[card.id]?.reference?.takeIf { it.isNotBlank() } ?: "B-${row.id.toString().take(6).uppercase()}-${row.nextCardNumber++}",
            completionDate = if (card.status == CardStatus.DONE) card.completionDate ?: java.time.LocalDate.now(java.time.ZoneOffset.UTC) else null) })
        row.name = request.name.trim(); row.contentJson = mapper.writeValueAsString(content); row.updatedAt = Instant.now()
        val saved = boards.saveAndFlush(row)
        val cards = request.content.cards.map { it.id }.toSet()
        development?.let { repository -> repository.deleteAll(repository.findByBoard_Id(saved.id).filter { it.cardId != null && it.cardId !in cards }) }
        val removed = previous.cards.map { it.id }.filter { it !in cards }
        if (removed.isNotEmpty()) comments?.deleteByBoardIdAndCardIdIn(saved.id, removed)
        search?.index(saved, content)
        return view(saved)
    }
    private fun actor(email: String) = actors.current(email).also { plugins.requireTable("kanban", "plugin_kanban_boards", it.role) }
    private fun owned(actor: User, id: UUID) = boards.findById(id).orElse(null)?.takeIf { projects?.readable(actor, it) ?: (it.ownerId == actor.id || com.starter.access.Roles.admin(actor.role)) }
        ?: throw NotFoundException("Board unavailable")
    private fun view(row: KanbanBoard) = KanbanView(row.id, row.ownerId, row.name, row.updatedAt, row.revision, mapper.readValue(row.contentJson, KanbanContent::class.java), row.projectId)
    internal fun validate(request: KanbanRequest) {
        val columns = request.content.columns; val cards = request.content.cards
        if (request.name.isBlank() || request.name.length > 120 || columns.size !in 1..20 || cards.size > 200) throw BadRequestException("Use a board name, 1–20 lists, and at most 200 cards")
        if (columns.map { it.id }.distinct().size != columns.size || cards.map { it.id }.distinct().size != cards.size) throw BadRequestException("List and card IDs must be unique")
        val ids = columns.map { it.id }.toSet()
        if (cards.any { listOfNotNull(it.startDate, it.dueDate, it.completionDate).any { date -> date.year !in 1900..9999 } ||
            it.startDate != null && it.dueDate != null && it.startDate > it.dueDate || it.startDate != null && it.completionDate != null && it.startDate > it.completionDate ||
            it.storyPoints?.let { points -> !points.isFinite() || points < 0 || points > 1000 } == true || it.reviewerIds.size > 20 }) throw BadRequestException("Choose valid dates, up to 1000 story points, and up to 20 reviewers")
        val sprints = request.content.sprints
        if (sprints.size > 100 || sprints.map { it.id }.distinct().size != sprints.size || sprints.any { it.name.isBlank() || it.name.length > 100 || it.endDate < it.startDate || it.startDate.year !in 1900..9999 || it.endDate.year !in 1900..9999 } || cards.any { it.sprintId != null && sprints.none { sprint -> sprint.id == it.sprintId } }) throw BadRequestException("Use valid named sprints and link cards to existing sprints")
        if (columns.any { it.title.isBlank() || it.title.length > 80 } || cards.any { it.columnId !in ids || it.title.isBlank() || it.title.length > 160 || it.description.length > 2000 })
            throw BadRequestException("Use valid lists and cards; card descriptions are limited to 2000 characters")
    }
}
