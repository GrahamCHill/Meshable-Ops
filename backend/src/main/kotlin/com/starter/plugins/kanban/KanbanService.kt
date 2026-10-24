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
    private val search: KanbanSearchProvider? = null) {
    fun list(email: String): List<KanbanSummary> { val actor = actor(email); return boards.visible(actor.id, actor.role == "ADMIN", PageRequest.of(0, 100)) }
    fun get(email: String, id: UUID) = view(owned(actor(email), id))
    @Transactional
    fun save(email: String, id: UUID?, request: KanbanRequest): KanbanView {
        val actor = actor(email)
        plugins.requireTable("kanban", "plugin_kanban_boards", actor.role, write = true)
        validate(request)
        val row = id?.let { owned(actor, it) } ?: KanbanBoard(ownerId = actor.id)
        if (id != null && row.revision != request.revision) throw ConflictException("This board changed. Reload before editing.")
        row.name = request.name.trim(); row.contentJson = mapper.writeValueAsString(request.content); row.updatedAt = Instant.now()
        val saved = boards.saveAndFlush(row)
        search?.index(saved, request.content)
        return view(saved)
    }
    private fun actor(email: String) = actors.current(email).also { plugins.requireTable("kanban", "plugin_kanban_boards", it.role) }
    private fun owned(actor: User, id: UUID) = boards.findById(id).orElse(null)?.takeIf { it.ownerId == actor.id || actor.role == "ADMIN" }
        ?: throw NotFoundException("Board unavailable")
    private fun view(row: KanbanBoard) = KanbanView(row.id, row.ownerId, row.name, row.updatedAt, row.revision, mapper.readValue(row.contentJson, KanbanContent::class.java))
    internal fun validate(request: KanbanRequest) {
        val columns = request.content.columns; val cards = request.content.cards
        if (request.name.isBlank() || request.name.length > 120 || columns.size !in 1..20 || cards.size > 200) throw BadRequestException("Use a board name, 1–20 lists, and at most 200 cards")
        if (columns.map { it.id }.distinct().size != columns.size || cards.map { it.id }.distinct().size != cards.size) throw BadRequestException("List and card IDs must be unique")
        val ids = columns.map { it.id }.toSet()
        if (columns.any { it.title.isBlank() || it.title.length > 80 } || cards.any { it.columnId !in ids || it.title.isBlank() || it.title.length > 160 || it.description.length > 2000 })
            throw BadRequestException("Use valid lists and cards; card descriptions are limited to 2000 characters")
    }
}
