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
    private val validation: WorkflowValidation,private val mapper: ObjectMapper,private val rates: RateLimits,private val alerts: com.starter.plugins.workflows.WorkflowNotificationHooks, private val controls: com.starter.access.AccessControlService? = null) {
    fun available(email: String): List<RunnableWorkflow> {
        val actor=guard.actor(email)
        return definitions.runnable((com.starter.access.Roles.admin(actor.role) && controls?.allows("admin.workflows") != false),PageRequest.of(0,100)).map {
            val graph = mapper.readValue(it.graphJson,WorkflowGraph::class.java)
            val blocked = graph.nodes.any { block -> block.kind in setOf(BlockKind.DOCUMENT,BlockKind.EMAIL) } && runCatching { guard.documentSupport(actor) }.isFailure
            RunnableWorkflow(it.id,it.name,it.revision,it.published,graph.nodes.size,if (blocked) "The Document generator plugin or its PDF access is unavailable." else null)
        }
    }
    fun list(email: String) = runs.owned(guard.actor(email).id,PageRequest.of(0,100))
    @Transactional
    fun start(email: String,id: UUID): WorkflowRunView {
        val actor=guard.actor(email);guard.writable(actor);rates.check("workflow-start",actor.id.toString(),20,3600)
        val source=definitions.findById(id).orElse(null)?.takeIf { it.state==WorkflowState.READY && (it.published || (com.starter.access.Roles.admin(actor.role) && controls?.allows("admin.workflows") != false)) } ?: throw NotFoundException("Published workflow unavailable")
        val graph=mapper.readValue(source.graphJson,WorkflowGraph::class.java)
        val ids: List<UUID> = mapper.readValue(source.documentIdsJson,object:TypeReference<List<UUID>>() {})
        validation.check(graph,ids,true)
        if (graph.nodes.any { it.kind in setOf(BlockKind.DOCUMENT, BlockKind.EMAIL) }) guard.documentSupport(actor)
        val row=WorkflowRun(ownerId=actor.id,workflowId=source.id,name=source.name,workflowRevision=source.revision,graphJson=source.graphJson,currentNodeId=graph.nodes.single {it.kind==BlockKind.START}.id)
        guard.active(guard.actor(email),row)
        val saved=runs.saveAndFlush(row);alerts.started(saved)
        return view(email,saved)
    }
    fun get(email: String,id: UUID) = view(email,guard.owned(guard.actor(email),id))
    @Transactional
    fun advance(email: String,id: UUID,request: WorkflowAdvanceRequest): WorkflowRunView {
        val actor=guard.actor(email);val row=guard.owned(actor,id,true);guard.active(actor,row)
        runtime.advance(row,request)
        val saved=runs.saveAndFlush(row);alerts.advanced(saved)
        return view(email,saved)
    }
    @Transactional
    fun useDocument(email: String,id: UUID,request: WorkflowDocumentRequest): WorkflowRunView {
        val actor=guard.actor(email);val row=guard.owned(actor,id,true);guard.active(actor,row);runtime.matches(row,request.revision,request.nodeId)
        if (row.emailState!=WorkflowEmailState.NONE) throw ConflictException("Resolve the current email attempt before replacing its document")
        val current=runtime.current(row)
        if (current.kind !in setOf(BlockKind.DOCUMENT,BlockKind.EMAIL) || current.documentId==null) throw BadRequestException("Select a PDF on a document or email step")
        access.document(actor,request.documentId) // Only a document already available in this member's own library.
        row.documentOverridesJson=mapper.writeValueAsString(runtime.overrides(row)+(current.documentId to request.documentId));row.updatedAt=Instant.now()
        return view(email,runs.saveAndFlush(row))
    }
    @Transactional
    fun cancel(email: String,id: UUID,revision: Long): WorkflowRunView {
        val row=guard.owned(guard.actor(email),id,true);runtime.matches(row,revision)
        if (runtime.emailBusy(row)) throw ConflictException("Wait for email delivery to finish before cancelling")
        row.status=WorkflowRunStatus.CANCELLED;row.currentNodeId=null;row.updatedAt=Instant.now()
        val saved=runs.saveAndFlush(row);alerts.cancelled(saved)
        return view(email,saved)
    }
    fun view(email: String,row: WorkflowRun): WorkflowRunView {
        val actor=guard.actor(email)
        if (row.ownerId!=actor.id) throw NotFoundException("Workflow run unavailable")
        val blocked=guard.blocked(actor,row)
        val ids=if (blocked==null) guard.allowedDocuments(row) else emptySet()
        return WorkflowRunView(row.id,row.name,row.workflowId,row.workflowRevision,row.status,row.revision,
            row.currentNodeId?.let {runtime.effective(row,runtime.current(row))},runtime.history(row),runtime.values(row),
            if (ids.isEmpty()) emptyList() else documents.workflowMetadata(ids.toList()),blocked,row.emailState,row.emailAttemptAt)
    }
}
