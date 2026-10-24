package com.starter.workflow

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.documents.*
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
    private val validation: WorkflowValidation,private val mapper: ObjectMapper,private val rates: RateLimits) {
    fun available(email: String): List<RunnableWorkflow> {
        val actor=guard.actor(email)
        return definitions.runnable(actor.role=="ADMIN",PageRequest.of(0,100)).map { RunnableWorkflow(it.id,it.name,it.revision,it.published,mapper.readValue(it.graphJson,WorkflowGraph::class.java).nodes.size) }
    }
    fun list(email: String) = runs.owned(guard.actor(email).id,PageRequest.of(0,100))
    @Transactional
    fun start(email: String,id: UUID): WorkflowRunView {
        val actor=guard.actor(email);rates.check("workflow-start",actor.id.toString(),20,3600)
        val source=definitions.findById(id).orElse(null)?.takeIf { it.state==WorkflowState.READY && (it.published || actor.role=="ADMIN") } ?: throw NotFoundException("Published workflow unavailable")
        val graph=mapper.readValue(source.graphJson,WorkflowGraph::class.java)
        val ids: List<UUID> = mapper.readValue(source.documentIdsJson,object:TypeReference<List<UUID>>() {})
        validation.check(graph,ids,true)
        val row=WorkflowRun(ownerId=actor.id,workflowId=source.id,name=source.name,workflowRevision=source.revision,graphJson=source.graphJson,currentNodeId=graph.nodes.single {it.kind==BlockKind.START}.id)
        guard.active(guard.actor(email),row)
        return view(email,runs.saveAndFlush(row))
    }
    fun get(email: String,id: UUID) = view(email,guard.owned(guard.actor(email),id))
    @Transactional
    fun advance(email: String,id: UUID,request: WorkflowAdvanceRequest): WorkflowRunView {
        val actor=guard.actor(email);val row=guard.owned(actor,id,true);guard.active(actor,row)
        runtime.advance(row,request)
        return view(email,runs.saveAndFlush(row))
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
        return view(email,runs.saveAndFlush(row))
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
