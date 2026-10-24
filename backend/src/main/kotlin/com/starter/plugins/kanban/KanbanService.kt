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
    private val projects: com.starter.projects.ProjectBoardAccess? = null, private val comments: KanbanCommentRepository? = null,
    private val estimates: KanbanEstimationService? = null) {
    fun list(email: String, projectId: UUID? = null): List<KanbanSummary> { val actor = actor(email); return projects?.list(actor, projectId) ?: boards.visible(actor.id, com.starter.access.Roles.admin(actor.role), PageRequest.of(0, 100)) }
    fun get(email: String, id: UUID) = actor(email).let { view(owned(it, id), it) }
    fun members(email: String, id: UUID) = actor(email).let { actor -> projects?.members(actor, owned(actor, id)) ?: emptyList() }
    fun creationPermissions(email: String, projectId: UUID?): Map<String, Boolean> {
        val actor = actor(email)
        val writable = runCatching { plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true) }.isSuccess
        return mapOf("canCreate" to (writable && mayCreate(actor, projectId)))
    }
    private fun mayCreate(actor: User, projectId: UUID?) = projects?.canCreate(actor, projectId)
        ?: (projectId == null && (com.starter.access.Roles.admin(actor.role) || actor.role == com.starter.access.Roles.BUSINESS_MANAGER))
    @Transactional
    fun save(email: String, id: UUID?, request: KanbanRequest): KanbanView {
        val actor = actor(email)
        plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true)
        if (id == null && !mayCreate(actor, request.projectId)) throw ForbiddenException("A board must be created by an administrator, Business Manager, project manager or team lead")
        val requested=KanbanCompletion.read(request.content)
        validate(request.copy(content=requested))
        val row = id?.let { owned(actor, it) } ?: KanbanBoard(ownerId = actor.id)
        if (id != null && row.revision != request.revision) throw ConflictException("This board changed. Reload before editing.")
        projects?.attach(actor, row, request.projectId)
        projects?.validateTeams(actor, row, request.projectId, request.teamIds)
        val previous = if (id == null) KanbanContent() else KanbanCompletion.read(mapper.readValue(row.contentJson, KanbanContent::class.java))
        val manager = canManage(actor, row)
        if (id != null && !manager && row.name != request.name.trim()) throw ForbiddenException("Only a board manager can rename this board")
        row.projectId = request.projectId
        row.teamIdsJson = mapper.writeValueAsString(request.teamIds)
        projects?.validatePeople(actor, row, requested.cards.flatMap { it.reviewerIds + listOfNotNull(it.assigneeId) }.toSet())
        val permissionMembers = (requested.columns.flatMap { it.enter.userIds + it.leave.userIds } + requested.workflow.transitions.flatMap { it.permission.userIds }).toSet()
        val previousPermissionMembers = (previous.columns.flatMap { it.enter.userIds + it.leave.userIds } + previous.workflow.transitions.flatMap { it.permission.userIds }).toSet()
        projects?.validatePeople(actor, row, permissionMembers - previousPermissionMembers)
        val old = previous.cards.associateBy { it.id }
        val now=Instant.now()
        val normalized = requested.copy(cards = requested.cards.map { card ->
            val status = requested.columns.first { it.id == card.columnId }.status ?: card.status
            card.copy(status = status, tags = card.tags.map(String::trim),
            createdAt = old[card.id]?.createdAt ?: now.takeIf {card.id !in old},
            reference = old[card.id]?.reference?.takeIf { it.isNotBlank() } ?: "B-${row.id.toString().take(6).uppercase()}-${row.nextCardNumber++}",
            startDate = if (status == CardStatus.IN_PROGRESS) card.startDate ?: java.time.LocalDate.now(java.time.ZoneOffset.UTC) else card.startDate,
            completionDate = if (status == CardStatus.DONE) card.completionDate ?: old[card.id]?.completionDate ?: java.time.LocalDate.now(java.time.ZoneOffset.UTC).takeIf {old[card.id]?.status != CardStatus.DONE} else null) })
        val completedSprints = previous.sprints.filter { it.status == SprintStatus.COMPLETED }.map { it.id }.toSet()
        val parents = normalized.cards.filter { it.parentId == null }.associate { card ->
            card.id to if (card.status != CardStatus.DONE && card.sprintId in completedSprints && old[card.id]?.sprintId == card.sprintId) card.copy(sprintId = null) else card
        }
        val content = normalized.copy(cards = normalized.cards.map { card ->
            if (card.parentId == null) parents.getValue(card.id) else card.copy(sprintId = parents[card.parentId]?.sprintId)
        })
        KanbanRules.check(actor.id, previous, content, id == null || manager)
        validate(request.copy(content = content))
        row.name = request.name.trim(); row.contentJson = mapper.writeValueAsString(content); row.updatedAt = Instant.now()
        val saved = boards.saveAndFlush(row)
        val cards = requested.cards.map { it.id }.toSet()
        development?.let { repository -> repository.deleteAll(repository.findByBoard_Id(saved.id).filter { it.cardId != null && it.cardId !in cards }) }
        val removed = previous.cards.map { it.id }.filter { it !in cards }
        if (removed.isNotEmpty()) comments?.deleteByBoardIdAndCardIdIn(saved.id, removed)
        search?.index(saved, content)
        return view(saved, actor)
    }
    @Transactional
    fun move(email: String, id: UUID, cardId: UUID, request: MoveTicketRequest): KanbanView {
        val actor = actor(email)
        plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true)
        val row = boards.locked(id).orElse(null)?.takeIf { projects?.readable(actor, it) ?: (it.ownerId == actor.id || com.starter.access.Roles.admin(actor.role)) }
            ?: throw NotFoundException("Board unavailable")
        if (row.revision != request.revision) throw ConflictException("This board changed. Reload before moving this ticket.")
        val content = KanbanCompletion.read(mapper.readValue(row.contentJson, KanbanContent::class.java))
        val card = content.cards.find { it.id == cardId } ?: throw NotFoundException("Ticket unavailable")
        val destination = content.columns.find { it.id == request.columnId } ?: throw BadRequestException("Destination column unavailable")
        val source = content.columns.find { it.id == card.columnId } ?: throw BadRequestException("Saved column unavailable")
        if (!KanbanRules.canMove(actor.id, card, source, destination, canManage(actor, row), content.workflow)) throw ForbiddenException("You cannot move this ticket between these columns")
        val cards = content.cards.filter { it.id != cardId }.toMutableList()
        val target = request.beforeId?.let { before -> cards.indexOfFirst { it.id == before && it.columnId == request.columnId }.takeIf { it >= 0 }
            ?: throw BadRequestException("The target ticket is unavailable in that column") } ?: cards.size
        cards.add(target, card.copy(columnId = request.columnId))
        return save(email, id, KanbanRequest(row.name, content.copy(cards = cards), row.revision, row.projectId,
            mapper.readValue(row.teamIdsJson, object : com.fasterxml.jackson.core.type.TypeReference<Set<UUID>>() {})))
    }
    @Transactional
    fun startSprint(email: String, id: UUID, sprintId: UUID, request: SprintActionRequest): KanbanView {
        val row = sprintBoard(email, id, request.revision)
        return saveSprint(email, row, KanbanSprintRules.start(KanbanCompletion.read(mapper.readValue(row.contentJson, KanbanContent::class.java)), sprintId))
    }
    @Transactional
    fun completeSprint(email: String, id: UUID, sprintId: UUID, request: SprintActionRequest): KanbanView {
        val row = sprintBoard(email, id, request.revision)
        return saveSprint(email, row, KanbanSprintRules.complete(KanbanCompletion.read(mapper.readValue(row.contentJson, KanbanContent::class.java)), sprintId, request.destinationSprintId))
    }
    private fun sprintBoard(email: String, id: UUID, revision: Long): KanbanBoard {
        val actor = actor(email)
        plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true)
        val row = boards.locked(id).orElse(null)?.takeIf { projects?.readable(actor, it) ?: (it.ownerId == actor.id || com.starter.access.Roles.admin(actor.role)) }
            ?: throw NotFoundException("Board unavailable")
        if (!canManage(actor, row)) throw ForbiddenException("Only a board manager can start or complete sprints")
        if (row.revision != revision) throw ConflictException("This board changed. Reload before changing its sprint.")
        return row
    }
    private fun saveSprint(email: String, row: KanbanBoard, content: KanbanContent) = save(email, row.id,
        KanbanRequest(row.name, content, row.revision, row.projectId,
            mapper.readValue(row.teamIdsJson, object : com.fasterxml.jackson.core.type.TypeReference<Set<UUID>>() {})))
    private fun actor(email: String) = actors.current(email).also { plugins.requireTable("kanban", "plugin_kanban_boards", it.role) }
    private fun owned(actor: User, id: UUID) = boards.findById(id).orElse(null)?.takeIf { projects?.readable(actor, it) ?: (it.ownerId == actor.id || com.starter.access.Roles.admin(actor.role)) }
        ?: throw NotFoundException("Board unavailable")
    private fun canManage(actor: User, row: KanbanBoard) = projects?.canManage(actor, row) ?: (row.ownerId == actor.id || com.starter.access.Roles.admin(actor.role))
    private fun view(row: KanbanBoard, actor: User): KanbanView {
        val content = KanbanCompletion.read(mapper.readValue(row.contentJson, KanbanContent::class.java))
        val manager = canManage(actor, row)
        val writable = runCatching { plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true) }.isSuccess
        val moves = content.cards.associate { card -> card.id to if (!writable) emptySet() else content.columns.filter { column ->
            val from = content.columns.find { it.id == card.columnId }
            from != null && KanbanRules.canMove(actor.id, card, from, column, manager, content.workflow)
        }.map { it.id }.toSet() }
        return KanbanView(row.id, row.ownerId, row.name, row.updatedAt, row.revision, content, row.projectId,
            mapper.readValue(row.teamIdsJson, object : com.fasterxml.jackson.core.type.TypeReference<Set<UUID>>() {}), manager && writable, moves, estimates?.effective(row.projectId) ?: KanbanEstimation())
    }
    internal fun validate(request: KanbanRequest) {
        KanbanRules.validate(request.content)
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
