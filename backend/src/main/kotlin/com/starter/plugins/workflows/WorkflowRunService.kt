package com.starter.plugins.workflows

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.plugins.documents.*
import com.starter.exception.*
import com.starter.security.RateLimits
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkflowRunService(private val definitions: WorkflowRepository,private val runs: WorkflowRunRepository,private val guard: WorkflowRunAccess,
    private val access: DocumentAccess,private val documents: GeneratedDocumentRepository,private val runtime: WorkflowRuntime,
    private val validation: WorkflowValidation,private val mapper: ObjectMapper,private val rates: RateLimits,private val alerts: com.starter.plugins.workflows.WorkflowNotificationHooks, private val controls: com.starter.access.AccessControlService? = null,
    private val audit: com.starter.service.AuditLogService? = null) {
    fun available(email: String): List<RunnableWorkflow> {
        val actor=guard.actor(email);guard.readableDesigns(actor)
        return definitions.runnable((com.starter.access.Roles.admin(actor.role) && controls?.allows("admin.workflows") != false),PageRequest.of(0,100)).map {
            val graph = mapper.readValue(it.graphJson,WorkflowGraph::class.java)
            val blocked = graph.nodes.any { block -> block.kind in setOf(BlockKind.DOCUMENT,BlockKind.EMAIL) } && runCatching { guard.documentSupport(actor) }.isFailure
            RunnableWorkflow(it.id,it.name,it.revision,it.published,graph.nodes.size,if (blocked) "The Document generator plugin or its PDF access is unavailable." else null)
        }
    }
    fun list(email: String) = runs.owned(guard.actor(email).id,PageRequest.of(0,100))
    fun reviews(email: String): List<WorkflowRunSummary> {
        val actor = guard.actor(email)
        return runs.awaitingReview(actor.id.toString(), PageRequest.of(0, 100)).filter { guard.canReview(actor, it) && guard.blocked(actor, it) == null }
            .map { WorkflowRunSummary(it.id, it.name, it.workflowId, it.status, it.updatedAt) }
    }
    @Transactional
    fun start(email: String,id: UUID, context: Map<String,String> = emptyMap(),reference: String = ""): WorkflowRunView {
        val actor=guard.actor(email);guard.writable(actor);guard.readableDesigns(actor);rates.check("workflow-start",actor.id.toString(),20,3600)
        val key=reference.trim().takeIf {it.isNotEmpty()}
        if(key!=null && (key.length>120 || key.any {it.isISOControl()}))throw BadRequestException("Use a business reference of up to 120 printable characters")
        val source=(if(key==null)definitions.findById(id) else definitions.locked(id)).orElse(null)?.takeIf { it.state==WorkflowState.READY && (it.published || (com.starter.access.Roles.admin(actor.role) && controls?.allows("admin.workflows") != false)) } ?: throw NotFoundException("Published workflow unavailable")
        if(key!=null && runs.findByWorkflowIdAndOwnerIdAndCorrelationKey(id,actor.id,key).isPresent)throw ConflictException("Your account already has a run in this workflow for that business reference")
        val graph=mapper.readValue(source.graphJson,WorkflowGraph::class.java)
        val ids: List<UUID> = mapper.readValue(source.documentIdsJson,object:TypeReference<List<UUID>>() {})
        validation.check(graph,ids,true)
        if (graph.nodes.any { it.kind in setOf(BlockKind.DOCUMENT, BlockKind.EMAIL) }) guard.documentSupport(actor)
        if (context.size > 60 || context.any { !it.key.startsWith("trigger.") || it.value.length > 500 }) throw BadRequestException("Workflow trigger context exceeds its limits")
        val row=WorkflowRun(ownerId=actor.id,workflowId=source.id,name=source.name,workflowRevision=source.revision,graphJson=source.graphJson,valuesJson=mapper.writeValueAsString(context),correlationKey=key)
        runtime.initialize(row)
        guard.active(guard.actor(email),row)
        val saved=runs.saveAndFlush(row);alerts.started(saved)
        audit?.record(email,"WORKFLOW_STARTED","WORKFLOW_RUN",saved.id.toString())
        return view(email,saved)
    }
    fun get(email: String,id: UUID,nodeId: String? = null) = view(email,guard.participant(guard.actor(email),id),nodeId)
    @Transactional
    fun advance(email: String,id: UUID,request: WorkflowAdvanceRequest): WorkflowRunView {
        val actor=guard.actor(email);val row=guard.participant(actor,id,true);guard.active(actor,row)
        if (runtime.block(row,request.nodeId).kind == BlockKind.REVIEW && !guard.canReview(actor,row,request.nodeId)) throw ForbiddenException("This step requires an assigned reviewer")
        if (row.ownerId != actor.id && request.action !in setOf(WorkflowAction.REVIEWED,WorkflowAction.REJECT)) throw ForbiddenException("Reviewers can only complete their assigned review")
        runtime.advance(row,request,actor.id)
        val visit=runtime.history(row).last {it.nodeId==request.nodeId}
        audit?.record(email,"WORKFLOW_STEP_${visit.outcome}","WORKFLOW_RUN",row.id.toString())
        val saved=runs.saveAndFlush(row);alerts.advanced(saved)
        if (row.ownerId != actor.id) return WorkflowRunView(row.id,row.name,row.workflowId,row.workflowRevision,row.status,row.revision,
            null,listOf(visit),emptyMap(),emptyList(),null,row.emailState,row.emailAttemptAt,false,false)
        return view(email,saved)
    }
    @Transactional
    fun useDocument(email: String,id: UUID,request: WorkflowDocumentRequest): WorkflowRunView {
        val actor=guard.actor(email);val row=guard.owned(actor,id,true);guard.active(actor,row);runtime.matches(row,request.revision,request.nodeId)
        if (row.emailState!=WorkflowEmailState.NONE) throw ConflictException("Resolve the current email attempt before replacing its document")
        val current=runtime.block(row,request.nodeId)
        if (current.kind !in setOf(BlockKind.DOCUMENT,BlockKind.EMAIL) || current.documentId==null) throw BadRequestException("Select a PDF on a document or email step")
        access.document(actor,request.documentId) // Only a document already available in this member's own library.
        row.documentOverridesJson=mapper.writeValueAsString(runtime.overrides(row)+(current.documentId to request.documentId));row.updatedAt=Instant.now()
        return view(email,runs.saveAndFlush(row),request.nodeId)
    }
    @Transactional
    fun cancel(email: String,id: UUID,revision: Long): WorkflowRunView {
        val row=guard.owned(guard.actor(email),id,true);runtime.matches(row,revision)
        if (runtime.emailBusy(row)) throw ConflictException("Wait for email delivery to finish before cancelling")
        row.executionJson=mapper.writeValueAsString(runtime.execution(row).copy(active=emptySet()))
        row.status=WorkflowRunStatus.CANCELLED;row.currentNodeId=null;row.updatedAt=Instant.now()
        val saved=runs.saveAndFlush(row);alerts.cancelled(saved)
        audit?.record(email,"WORKFLOW_CANCELLED","WORKFLOW_RUN",row.id.toString())
        return view(email,saved)
    }
    fun view(email: String,row: WorkflowRun,nodeId: String? = null): WorkflowRunView {
        val actor=guard.actor(email)
        val owner = row.ownerId == actor.id
        if (!owner && !guard.canReview(actor,row)) throw NotFoundException("Workflow run unavailable")
        val blocked=guard.blocked(actor,row)
        val ids=if (blocked==null && owner) guard.allowedDocuments(row) else emptySet()
        val active=if(row.status==WorkflowRunStatus.IN_PROGRESS)runtime.active(row) else emptyList()
        val visible=active.filter {owner || guard.canReview(actor,row,it.id)}
        val current=(nodeId?.let {chosen -> visible.find {it.id==chosen} ?: throw NotFoundException("Workflow step unavailable")}
            ?: visible.find {it.id==row.currentNodeId && it.kind!=BlockKind.WEBHOOK}
            ?: visible.firstOrNull {it.kind!=BlockKind.WEBHOOK} ?: visible.firstOrNull())
        val permitted=visible.filter {it.kind!=BlockKind.WEBHOOK && (it.kind!=BlockKind.REVIEW && owner || guard.canReview(actor,row,it.id))}.map {it.id}.toSet()
        fun redact(block: WorkflowBlock)=runtime.effective(row,block).let {if(owner)it else it.copy(documentId=null,code="")}
        return WorkflowRunView(row.id,row.name,row.workflowId,row.workflowRevision,row.status,row.revision,
            current?.let(::redact),if(owner)runtime.history(row) else emptyList(),
            runtime.values(row).filterKeys {owner || it.startsWith("trigger.") || it.startsWith("webhook.") || it in WorkflowExportOptions.FIELDS},
            if(ids.isEmpty())emptyList() else documents.workflowMetadata(ids.toList()),blocked,row.emailState,row.emailAttemptAt,
            blocked==null && current?.id in permitted,owner,visible.filter {it.kind!=BlockKind.WEBHOOK}.map(::redact),
            if(blocked==null)permitted else emptySet(),if(owner)runtime.waiting(row) else emptyList(),if(owner)row.correlationKey else null)
    }
}
