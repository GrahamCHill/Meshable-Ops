package com.starter.plugins.workflows

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.plugins.documents.GeneratedDocumentView
import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

enum class WorkflowRunStatus { IN_PROGRESS, COMPLETED, CANCELLED, REJECTED }
enum class WorkflowEmailState { NONE, SENDING, UNCERTAIN }
enum class WorkflowAction { CONTINUE, REVIEWED, REJECT, EVALUATE, CONFIRM_EMAIL }
data class WorkflowVisit(val nodeId: String,val label: String,val kind: BlockKind,val outcome: String,val note: String,val completedAt: Instant,
    val actorId: UUID? = null, val audited: Boolean = false)
data class WorkflowStartRequest(val workflowId: UUID, @field:Size(max=120) val reference: String = "")
data class WorkflowWaitingNode(val id: String,val label: String,val kind: BlockKind,val received: Int,val required: Int,
    val missing: List<String> = emptyList(), val endpointId: UUID? = null,val event: String = "")
data class WorkflowAdvanceRequest(@field:Min(0) val revision: Long,val nodeId: String,val action: WorkflowAction = WorkflowAction.CONTINUE,
    @field:Size(max=500) val value: String = "",@field:Size(max=1000) val note: String = "")
data class WorkflowDocumentRequest(@field:Min(0) val revision: Long,val nodeId: String,val documentId: UUID)
data class WorkflowSendRequest(@field:Min(0) val revision: Long,val nodeId: String,@field:com.starter.security.PasswordBytes @get:com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) val password: String = "",
    @field:Size(max=1000) val note: String = "") {
    override fun toString() = "WorkflowSendRequest(nodeId=$nodeId)"
}
data class WorkflowRevisionRequest(@field:Min(0) val revision: Long)
data class RunnableWorkflow(val id: UUID,val name: String,val revision: Long,val published: Boolean,val stepCount: Int,val blockedReason: String? = null)
data class WorkflowRunSummary(val id: UUID,val name: String,val workflowId: UUID,val status: WorkflowRunStatus,val updatedAt: Instant)
data class WorkflowRunView(val id: UUID,val name: String,val workflowId: UUID,val workflowRevision: Long,val status: WorkflowRunStatus,val revision: Long,
    val current: WorkflowBlock?,val history: List<WorkflowVisit>,val values: Map<String,String>,val documents: List<GeneratedDocumentView>,
    val blockedReason: String?,val emailState: WorkflowEmailState,val emailAttemptAt: Instant?, val canAdvance: Boolean = true,
    val isOwner: Boolean = true, val readyNodes: List<WorkflowBlock> = emptyList(), val advanceableNodeIds: Set<String> = emptySet(),
    val waitingNodes: List<WorkflowWaitingNode> = emptyList(), val reference: String? = null)

@Entity @Table(name="workflow_runs",indexes=[Index(columnList="owner_id,updated_at")],uniqueConstraints=[UniqueConstraint(columnNames=["workflow_id","owner_id","correlation_key"])])
class WorkflowRun(@Id val id: UUID = UUID.randomUUID(),val ownerId: UUID = UUID.randomUUID(),val workflowId: UUID = UUID.randomUUID(),
    val name: String = "",val workflowRevision: Long = 0,
    @get:JsonIgnore @Column(columnDefinition="TEXT") val graphJson: String = "{}",
    var currentNodeId: String? = null,@Enumerated(EnumType.STRING) var status: WorkflowRunStatus = WorkflowRunStatus.IN_PROGRESS,
    @get:JsonIgnore @Column(columnDefinition="TEXT") var historyJson: String = "[]",
    @get:JsonIgnore @Column(columnDefinition="TEXT") var valuesJson: String = "{}",
    @get:JsonIgnore @Column(columnDefinition="TEXT") var documentOverridesJson: String = "{}",
    @Enumerated(EnumType.STRING) var emailState: WorkflowEmailState = WorkflowEmailState.NONE,var emailAttemptId: UUID? = null,var emailAttemptAt: Instant? = null,
    val createdAt: Instant = Instant.now(),var updatedAt: Instant = Instant.now(),@Version var revision: Long = 0,
    @get:JsonIgnore @Column(columnDefinition="TEXT",nullable=false) @org.hibernate.annotations.ColumnDefault("'{}'") var executionJson: String = "{}",
    @Column(length=120) var correlationKey: String? = null) {
    override fun toString() = "WorkflowRun(id=$id,status=$status)"
}
interface WorkflowRunRepository : JpaRepository<WorkflowRun,UUID> {
    fun findByWorkflowIdAndOwnerIdAndCorrelationKey(workflowId: UUID,ownerId: UUID,correlationKey: String): java.util.Optional<WorkflowRun>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT r FROM WorkflowRun r WHERE r.id = :id")
    fun locked(id: UUID): java.util.Optional<WorkflowRun>
    @Query("SELECT new com.starter.plugins.workflows.WorkflowRunSummary(r.id,r.name,r.workflowId,r.status,r.updatedAt) FROM WorkflowRun r WHERE r.ownerId = :owner ORDER BY r.updatedAt DESC")
    fun owned(owner: UUID,pageable: Pageable): List<WorkflowRunSummary>
    @Query(value = """SELECT r.* FROM {h-schema}workflow_runs r WHERE r.status='IN_PROGRESS' AND EXISTS (
        SELECT 1 FROM jsonb_array_elements(CAST(r.graph_json AS jsonb)->'nodes') n
        WHERE (n->>'id'=r.current_node_id OR jsonb_exists(COALESCE(CAST(r.execution_json AS jsonb)->'active', '[]'::jsonb),n->>'id')) AND n->>'kind'='REVIEW'
        AND jsonb_exists(COALESCE(n->'reviewerIds', '[]'::jsonb), :reviewer)) ORDER BY r.updated_at""", nativeQuery = true)
    fun awaitingReview(reviewer: String, pageable: Pageable): List<WorkflowRun>
}
