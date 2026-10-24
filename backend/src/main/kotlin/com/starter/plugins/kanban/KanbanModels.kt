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
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var contentJson: String = "{}")

data class KanbanColumn(val id: UUID, val title: String)
data class KanbanCard(val id: UUID, val columnId: UUID, val title: String, val description: String = "", val dueDate: java.time.LocalDate? = null)
data class KanbanContent(val columns: List<KanbanColumn> = emptyList(), val cards: List<KanbanCard> = emptyList())
data class KanbanRequest(val name: String, val content: KanbanContent, val revision: Long = 0)
data class KanbanSummary(val id: UUID, val ownerId: UUID, val name: String, val updatedAt: Instant)
data class KanbanView(val id: UUID, val ownerId: UUID, val name: String, val updatedAt: Instant, val revision: Long, val content: KanbanContent)

interface KanbanRepository : JpaRepository<KanbanBoard, UUID> {
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM KanbanBoard b WHERE b.id = :id")
    fun locked(id: UUID): java.util.Optional<KanbanBoard>
    @Query("SELECT new com.starter.plugins.kanban.KanbanSummary(b.id, b.ownerId, b.name, b.updatedAt) FROM KanbanBoard b WHERE (:admin = true OR b.ownerId = :owner) ORDER BY b.updatedAt DESC")
    fun visible(owner: UUID, admin: Boolean, pageable: Pageable): List<KanbanSummary>
}
