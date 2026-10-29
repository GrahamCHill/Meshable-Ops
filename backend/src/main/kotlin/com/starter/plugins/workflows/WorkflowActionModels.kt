package com.starter.plugins.workflows

import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.jpa.repository.*
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

data class WorkflowActionDescriptor(val id: String, val label: String, val pluginId: String, val external: Boolean = false,
    val method: String = "", val route: String = "", val fields: List<String> = emptyList(), val outputs: List<String> = emptyList())
data class WorkflowBindingRequest(@field:Size(max=300) val selector: String, val workflowId: UUID, val enabled: Boolean = true,
    @field:Size(max=120) val contextField: String = "", @field:Size(max=500) val contextEquals: String = "", @field:Min(0) val revision: Long = 0)
data class WorkflowBindingView(val id: UUID, val selector: String, val workflowId: UUID, val enabled: Boolean,
    val contextField: String, val contextEquals: String, val revision: Long, val diagramManaged: Boolean = false)

@Entity @Table(name="workflow_action_bindings")
class WorkflowActionBinding(@Id val id: UUID = UUID.randomUUID(), @Column(length=300) var selector: String = "", var workflowId: UUID = UUID.randomUUID(),
    var enabled: Boolean = true, var contextField: String = "", @Column(length=500) var contextEquals: String = "", @Version var revision: Long = 0,
    @Column(unique=true) var diagramWorkflowId: UUID? = null)
interface WorkflowActionBindingRepository : JpaRepository<WorkflowActionBinding,UUID> {
    fun findByEnabledTrueOrderById(): List<WorkflowActionBinding>
    fun findByDiagramWorkflowId(diagramWorkflowId: UUID): WorkflowActionBinding?
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT b FROM WorkflowActionBinding b WHERE b.id=:id")
    fun locked(id: UUID): WorkflowActionBinding?
}

@Entity @Table(name="workflow_action_requests", indexes=[Index(columnList="owner_id,binding_id,fingerprint,state")])
class WorkflowActionRequest(@Id val id: UUID = UUID.randomUUID(), val ownerId: UUID = UUID.randomUUID(),
    val bindingId: UUID = UUID.randomUUID(), val bindingRevision: Long = 0, val workflowRevision: Long = 0,
    val runId: UUID = UUID.randomUUID(), @Column(length=64) val fingerprint: String = "", var state: String = "PENDING",
    val createdAt: Instant = Instant.now(), val expiresAt: Instant = Instant.now().plusSeconds(86400), var consumedAt: Instant? = null)
interface WorkflowActionRequestRepository : JpaRepository<WorkflowActionRequest,UUID> {
    fun findByOwnerIdAndBindingIdAndFingerprintAndStateOrderByCreatedAtDesc(ownerId: UUID, bindingId: UUID, fingerprint: String, state: String): List<WorkflowActionRequest>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT a FROM WorkflowActionRequest a WHERE a.id=:id")
    fun locked(id: UUID): WorkflowActionRequest?
}
data class WorkflowActionRequired(val message: String, val status: Int = 428, val workflowRequestId: UUID,
    val runId: UUID, val workflowName: String)
class WorkflowRequiredException(val detail: WorkflowActionRequired) : RuntimeException(detail.message)
