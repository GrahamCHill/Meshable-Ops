package com.starter.plugins.kanban

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "plugin_kanban_development_links", indexes = [Index(columnList = "board_id")])
class KanbanDevelopmentLink(@Id val id: UUID = UUID.randomUUID(),
    @get:JsonIgnore @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "board_id", nullable = false) @OnDelete(action = OnDeleteAction.CASCADE) val board: KanbanBoard = KanbanBoard(),
    val cardId: UUID? = null, val providerId: String = "", val repository: String = "", val requestNumber: Int? = null,
    @Column(length = 300) var title: String = "", @Column(length = 2000) var url: String = "", var state: String = "", var checkedAt: Instant = Instant.now())
interface KanbanDevelopmentLinkRepository : JpaRepository<KanbanDevelopmentLink, UUID> { fun findByBoard_Id(id: UUID): List<KanbanDevelopmentLink> }
data class BoardLinkRequest(val providerId: String, val repository: String, val number: Int? = null, val cardId: UUID? = null)
data class BoardLinkView(val id: UUID, val providerId: String, val repository: String?, val number: Int?, val cardId: UUID?,
    val title: String, val url: String?, val state: String?, val checkedAt: Instant?, val available: Boolean)
