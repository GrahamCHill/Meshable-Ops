package com.starter.plugins.workflows

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.plugins.documents.DocumentEmailProtection
import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

enum class WorkflowState { DRAFT, READY, ARCHIVED }
enum class BlockKind { START, DOCUMENT, REVIEW, CONDITION, EMAIL, END }
enum class Branch { NEXT, TRUE, FALSE }
enum class ConditionOperator { EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, IS_SET }
data class WorkflowBlock(@field:Pattern(regexp = "[A-Za-z0-9_-]{1,80}") val id: String, val kind: BlockKind,
    @field:NotBlank @field:Size(max = 80) val label: String,
    @field:DecimalMin("-10000") @field:DecimalMax("10000") val x: Double = 0.0,
    @field:DecimalMin("-10000") @field:DecimalMax("10000") val y: Double = 0.0,
    val documentId: UUID? = null, @field:Size(max = 500) val note: String = "",
    @field:Email @field:Size(max = 254) val recipient: String = "", @field:Size(max = 200) val subject: String = "",
    val protection: DocumentEmailProtection = DocumentEmailProtection.PLAIN,
    @field:Size(max = 120) val field: String = "", val operator: ConditionOperator = ConditionOperator.EQUALS,
    @field:Size(max = 500) val expected: String = "")
data class WorkflowEdge(@field:Pattern(regexp = "[A-Za-z0-9_-]{1,80}") val id: String,
    @field:Size(max = 80) val source: String, @field:Size(max = 80) val target: String, val branch: Branch = Branch.NEXT)
data class WorkflowGraph(@field:Valid @field:Size(max = 50) val nodes: List<WorkflowBlock>, @field:Valid @field:Size(max = 100) val edges: List<WorkflowEdge>)
data class WorkflowRequest(@field:NotBlank @field:Size(max = 120) val name: String, val state: WorkflowState = WorkflowState.DRAFT,
    @field:Valid val graph: WorkflowGraph, @field:Size(max = 50) val documentIds: List<UUID> = emptyList(), @field:Min(0) val revision: Long = 0, val published: Boolean = false)
data class WorkflowView(val id: UUID, val ownerId: UUID, val name: String, val state: WorkflowState, val revision: Long,
    val updatedAt: Instant, val graph: WorkflowGraph, val documentIds: List<UUID>, val published: Boolean = false)

@Entity @Table(name = "workflow_definitions", indexes = [Index(columnList = "owner_id,updated_at")])
class WorkflowDefinition(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(), var name: String = "",
    @Enumerated(EnumType.STRING) var state: WorkflowState = WorkflowState.DRAFT,
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var graphJson: String = "{}",
    @get:JsonIgnore @Column(columnDefinition = "TEXT") var documentIdsJson: String = "[]",
    val createdAt: Instant = Instant.now(), var updatedAt: Instant = Instant.now(), @Version var revision: Long = 0,
    @Column(nullable=false) @org.hibernate.annotations.ColumnDefault("false") var published: Boolean = false) {
    override fun toString() = "WorkflowDefinition(id=$id)"
}
interface WorkflowRepository : JpaRepository<WorkflowDefinition, UUID> {
    @Query("SELECT w FROM WorkflowDefinition w WHERE w.state = com.starter.plugins.workflows.WorkflowState.READY AND (w.published = true OR :admin = true) ORDER BY w.updatedAt DESC")
    fun runnable(admin: Boolean,pageable: Pageable): List<WorkflowDefinition>
    @Query("SELECT w FROM WorkflowDefinition w WHERE (:admin = true OR w.ownerId = :owner) ORDER BY w.updatedAt DESC")
    fun visible(owner: UUID, admin: Boolean, pageable: Pageable): List<WorkflowDefinition>
}
