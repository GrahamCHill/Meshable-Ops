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
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("1") var nextCardNumber: Long = 1)

enum class CardStatus { TODO, IN_PROGRESS, DONE }
data class KanbanColumn(val id: UUID, val title: String, val status: CardStatus? = null)
data class KanbanCard(val id: UUID, val columnId: UUID, val title: String, val description: String = "", val dueDate: java.time.LocalDate? = null,
    val assigneeId: UUID? = null, val startDate: java.time.LocalDate? = null, val completionDate: java.time.LocalDate? = null,
    val sprintId: UUID? = null, val storyPoints: Double? = null, val reviewerIds: Set<UUID> = emptySet(), val status: CardStatus = CardStatus.TODO,
    val reference: String = "", val blocked: Boolean = false)
data class KanbanSprint(val id: UUID, val name: String, val startDate: java.time.LocalDate, val endDate: java.time.LocalDate)
data class KanbanContent(val columns: List<KanbanColumn> = emptyList(), val cards: List<KanbanCard> = emptyList(), val sprints: List<KanbanSprint> = emptyList())
data class KanbanRequest(val name: String, val content: KanbanContent, val revision: Long = 0, val projectId: UUID? = null)
data class KanbanSummary(val id: UUID, val ownerId: UUID, val name: String, val updatedAt: Instant, val projectId: UUID? = null)
data class KanbanView(val id: UUID, val ownerId: UUID, val name: String, val updatedAt: Instant, val revision: Long, val content: KanbanContent, val projectId: UUID? = null)

interface KanbanRepository : JpaRepository<KanbanBoard, UUID> {
    fun findByProjectId(projectId: UUID): List<KanbanBoard>
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM KanbanBoard b WHERE b.id = :id")
    fun locked(id: UUID): java.util.Optional<KanbanBoard>
    @Query("SELECT new com.starter.plugins.kanban.KanbanSummary(b.id, b.ownerId, b.name, b.updatedAt) FROM KanbanBoard b WHERE (:admin = true OR b.ownerId = :owner) ORDER BY b.updatedAt DESC")
    fun visible(owner: UUID, admin: Boolean, pageable: Pageable): List<KanbanSummary>
}
