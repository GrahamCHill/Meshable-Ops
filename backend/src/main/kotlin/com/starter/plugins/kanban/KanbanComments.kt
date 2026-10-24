package com.starter.plugins.kanban

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.notification.*
import com.starter.plugins.PluginActor
import com.starter.plugins.PluginRegistry
import com.starter.projects.ProjectBoardAccess
import com.starter.repository.postgres.UserRepository
import jakarta.persistence.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "plugin_kanban_comments", indexes = [Index(columnList = "board_id,card_id,created_at")])
class KanbanComment(@Id val id: UUID = UUID.randomUUID(), val boardId: UUID = UUID.randomUUID(), val cardId: UUID = UUID.randomUUID(),
    val authorId: UUID = UUID.randomUUID(), @Column(length = 4000) val text: String = "", @Column(columnDefinition = "TEXT") val mentionsJson: String = "[]", val createdAt: Instant = Instant.now())
interface KanbanCommentRepository : JpaRepository<KanbanComment, UUID> {
    fun findTop200ByBoardIdAndCardIdOrderByCreatedAtAsc(boardId: UUID, cardId: UUID): List<KanbanComment>
    fun deleteByBoardIdAndCardIdIn(boardId: UUID, cardIds: Collection<UUID>)
}
data class KanbanCommentInput(val text: String, val mentionIds: Set<UUID> = emptySet())
data class KanbanCommentView(val id: UUID, val authorId: UUID, val authorName: String, val text: String, val mentionIds: Set<UUID>, val createdAt: Instant)
data class KanbanMentionEvent(val commentId: UUID, val boardId: UUID, val cardId: UUID, val authorId: UUID, val recipients: Set<UUID>)

@Service
class KanbanCommentService(private val comments: KanbanCommentRepository, private val boards: KanbanRepository,
    private val actors: PluginActor, private val registry: PluginRegistry, private val access: ProjectBoardAccess,
    private val mapper: ObjectMapper, private val users: UserRepository, private val events: ApplicationEventPublisher) {
    fun list(email: String, boardId: UUID, cardId: UUID): List<KanbanCommentView> {
        val actor = actors.current(email); board(actor, boardId, cardId)
        registry.requireTable("kanban", "plugin_kanban_comments", actor.role)
        return comments.findTop200ByBoardIdAndCardIdOrderByCreatedAtAsc(boardId, cardId).map(::view)
    }
    @Transactional
    fun add(email: String, boardId: UUID, cardId: UUID, input: KanbanCommentInput): KanbanCommentView {
        val actor = actors.current(email); val row = board(actor, boardId, cardId, true)
        registry.requireTable("kanban", "plugin_kanban_comments", actor.role, true)
        if (input.text.isBlank() || input.text.length > 4000 || input.mentionIds.size > 20) throw BadRequestException("Use a comment of 1–4000 characters and up to 20 internal mentions")
        access.validatePeople(actor, row, input.mentionIds)
        if (comments.findTop200ByBoardIdAndCardIdOrderByCreatedAtAsc(boardId, cardId).size >= 200) throw BadRequestException("A card supports up to 200 comments")
        val saved = comments.saveAndFlush(KanbanComment(boardId = boardId, cardId = cardId, authorId = actor.id, text = input.text.trim(), mentionsJson = mapper.writeValueAsString(input.mentionIds)))
        events.publishEvent(KanbanMentionEvent(saved.id, boardId, cardId, actor.id, input.mentionIds - actor.id))
        return view(saved)
    }
    private fun board(actor: com.starter.model.postgres.User, boardId: UUID, cardId: UUID, lock: Boolean = false): KanbanBoard {
        registry.requireTable("kanban", "plugin_kanban_boards", actor.role)
        val row = (if (lock) boards.locked(boardId) else boards.findById(boardId)).orElseThrow { NotFoundException("Board unavailable") }; access.require(actor, row)
        if (mapper.readValue(row.contentJson, KanbanContent::class.java).cards.none { it.id == cardId }) throw NotFoundException("Card unavailable")
        return row
    }
    private fun view(row: KanbanComment) = KanbanCommentView(row.id, row.authorId, users.findById(row.authorId).orElse(null)?.let { it.name ?: it.username } ?: "Former member", row.text, mapper.readValue(row.mentionsJson, object : TypeReference<Set<UUID>>() {}), row.createdAt)
}

@Service
class KanbanMentions(private val publisher: NotificationPublisher, private val users: UserRepository, private val boards: KanbanRepository,
    private val access: ProjectBoardAccess, private val registry: PluginRegistry, private val mapper: ObjectMapper) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun notify(event: KanbanMentionEvent) {
        val board = boards.findById(event.boardId).orElse(null) ?: return
        val card = mapper.readValue(board.contentJson, KanbanContent::class.java).cards.find { it.id == event.cardId } ?: return
        event.recipients.forEach { id -> runCatching {
            val recipient = users.findById(id).orElse(null) ?: return@runCatching
            if (!access.readable(recipient, board)) return@runCatching
            registry.requireTable("kanban", "plugin_kanban_comments", recipient.role)
            publisher.publish("kanban.mention", id, mapOf("subject" to "You were mentioned: ${card.title}", "message" to "You were mentioned in a comment on ${card.reference.ifBlank { card.title }} in ${board.name}.",
                "cardTitle" to card.title, "boardName" to board.name, "link" to "/plugins/kanban/boards?board=${board.id}&card=${card.id}"), "kanban-mention:${event.commentId}:$id")
        } }
    }
}

@org.springframework.stereotype.Component
class KanbanNotificationDefinitions : NotificationDefinitionProvider {
    override val pluginId = "kanban"
    override fun definitions() = listOf(NotificationDefinition("kanban.mention", "Card mentions", "Notify an internal board member when tagged in a comment.", NotificationCategory.GENERAL,
        setOf("applicationName", "recipientName", "subject", "message", "cardTitle", "boardName", "link"), "{{subject}}", "{{message}} {{link}}"))
}

@RestController @RequestMapping("/api/plugins/kanban/boards/{boardId}/cards/{cardId}/comments")
class KanbanCommentController(private val comments: KanbanCommentService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails, @PathVariable boardId: UUID, @PathVariable cardId: UUID) = comments.list(user.username, boardId, cardId)
    @PostMapping fun add(@AuthenticationPrincipal user: UserDetails, @PathVariable boardId: UUID, @PathVariable cardId: UUID, @RequestBody input: KanbanCommentInput) = comments.add(user.username, boardId, cardId, input)
}
