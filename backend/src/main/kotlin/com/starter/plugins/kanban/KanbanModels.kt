package com.starter.plugins.kanban

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "plugin_kanban_boards", indexes = [Index(columnList = "owner_id,updated_at")])
class KanbanBoard(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(),
    var name: String = "", var updatedAt: Instant = Instant.now(), @Version var revision: Long = 0,
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var contentJson: String = "{}", var projectId: UUID? = null,
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("1") var nextCardNumber: Long = 1,
    @get:JsonIgnore @Column(columnDefinition = "TEXT DEFAULT '[]'", nullable = false) var teamIdsJson: String = "[]")

enum class CardStatus { TODO, IN_PROGRESS, DONE }
enum class MovePermission { EDITORS, ASSIGNEE, REVIEWERS, SELECTED_MEMBERS, MANAGERS }
data class ColumnPermission(val mode: MovePermission = MovePermission.EDITORS, val userIds: Set<UUID> = emptySet())
data class KanbanColumn(val id: UUID, val title: String, val status: CardStatus? = null,
    val enter: ColumnPermission = ColumnPermission(), val leave: ColumnPermission = ColumnPermission())
data class KanbanCard(val id: UUID, val columnId: UUID, val title: String, val description: String = "", val dueDate: java.time.LocalDate? = null,
    val assigneeId: UUID? = null, val startDate: java.time.LocalDate? = null, val completionDate: java.time.LocalDate? = null,
    val sprintId: UUID? = null, val storyPoints: Double? = null, val reviewerIds: Set<UUID> = emptySet(), val status: CardStatus = CardStatus.TODO,
    val reference: String = "", val blocked: Boolean = false, val tags: List<String> = emptyList(), val epicId: UUID? = null,
    val type: TicketType = TicketType.TASK, val priority: TicketPriority = TicketPriority.MEDIUM, val parentId: UUID? = null,
    val tshirtSize: TshirtSize? = null, val links: List<TicketLink> = emptyList(), val createdAt: Instant? = null)

enum class TshirtSize { XS, S, M, L, XL, XXL }
enum class TicketLinkType { RELATED, BLOCKS, BLOCKED_BY }
data class TicketLink(val targetId: UUID, val type: TicketLinkType = TicketLinkType.RELATED)
data class WorkflowPosition(val x: Double, val y: Double)
data class KanbanTransition(val id: UUID, val source: UUID, val target: UUID, val name: String = "Move",
    val permission: ColumnPermission = ColumnPermission())
data class KanbanWorkflow(val enabled: Boolean = false, val transitions: List<KanbanTransition> = emptyList(),
    val positions: Map<UUID, WorkflowPosition> = emptyMap())

enum class TicketType { TASK, STORY, BUG, SUBTASK }
enum class TicketPriority { LOW, MEDIUM, HIGH, URGENT }
data class KanbanEpic(val id: UUID, val name: String, val description: String = "", val color: String = "#6554c0")
enum class SprintStatus { PLANNED, ACTIVE, COMPLETED }
data class KanbanSprint(val id: UUID, val name: String, val startDate: java.time.LocalDate, val endDate: java.time.LocalDate,
    val status: SprintStatus = SprintStatus.PLANNED, val goal: String = "")
data class KanbanContent(val columns: List<KanbanColumn> = emptyList(), val cards: List<KanbanCard> = emptyList(), val sprints: List<KanbanSprint> = emptyList(), val epics: List<KanbanEpic> = emptyList(), val workflow: KanbanWorkflow = KanbanWorkflow())
data class KanbanRequest(val name: String, val content: KanbanContent, val revision: Long = 0, val projectId: UUID? = null, val teamIds: Set<UUID> = emptySet())
data class KanbanSummary @JvmOverloads constructor(val id: UUID, val ownerId: UUID, val name: String, val updatedAt: Instant, val projectId: UUID? = null, val ticketCount: Int = 0, val completedCount: Int = 0)
data class KanbanView(val id: UUID, val ownerId: UUID, val name: String, val updatedAt: Instant, val revision: Long, val content: KanbanContent, val projectId: UUID? = null, val teamIds: Set<UUID> = emptySet(), val canManage: Boolean = false,
    val moves: Map<UUID, Set<UUID>> = emptyMap(), val estimation: KanbanEstimation = KanbanEstimation())
data class MoveTicketRequest(val columnId: UUID, val revision: Long, val beforeId: UUID? = null)
data class SprintActionRequest(val revision: Long, val destinationSprintId: UUID? = null)

interface KanbanRepository : JpaRepository<KanbanBoard, UUID> {
    fun findByProjectId(projectId: UUID): List<KanbanBoard>
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM KanbanBoard b WHERE b.id = :id")
    fun locked(id: UUID): java.util.Optional<KanbanBoard>
    @Query("SELECT new com.starter.plugins.kanban.KanbanSummary(b.id, b.ownerId, b.name, b.updatedAt, b.projectId) FROM KanbanBoard b WHERE (:admin = true OR b.ownerId = :owner) ORDER BY b.updatedAt DESC")
    fun visible(owner: UUID, admin: Boolean, pageable: Pageable): List<KanbanSummary>
}
