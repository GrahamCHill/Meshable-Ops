package com.starter.plugins.workflows

import com.starter.plugins.documents.*
import com.starter.exception.*
import com.starter.model.postgres.User
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class WorkflowRunAccess(private val runs: WorkflowRunRepository,private val definitions: WorkflowRepository,
    private val access: DocumentAccess,private val documents: GeneratedDocumentRepository,private val runtime: WorkflowRuntime, private val modules: com.starter.plugins.BundledModuleAccess, private val controls: com.starter.access.AccessControlService? = null) {
    fun actor(email: String) = access.actor(email).also { modules.check("workflows", it, "workflow_runs", "workflows") }
    fun writable(actor: User) = modules.check("workflows", actor, "workflow_runs", "workflows", write = true)
    fun readableDesigns(actor: User) = modules.check("workflows", actor, "workflow_definitions", "workflows")
    fun documentSupport(actor: User) = modules.documents(actor)
    fun owned(actor: User,id: UUID,lock: Boolean = false): WorkflowRun {
        if (lock) writable(actor)
        return (if (lock) runs.locked(id) else runs.findById(id)).orElse(null)?.takeIf { it.ownerId==actor.id } ?: throw NotFoundException("Workflow run unavailable")
    }
    fun canReview(actor: User,run: WorkflowRun,nodeId: String? = null): Boolean {
        if(!actor.active || actor.role=="EXTERNAL" || run.status!=WorkflowRunStatus.IN_PROGRESS)return false
        return runtime.active(run).any {block -> (nodeId==null || nodeId==block.id) && block.kind==BlockKind.REVIEW &&
            (block.allowInitiatorReview || actor.id!=run.ownerId) && (actor.id in block.reviewerIds || actor.id==run.ownerId && block.allowInitiatorReview)}
    }
    fun participant(actor: User, id: UUID, lock: Boolean = false): WorkflowRun {
        if (lock) writable(actor)
        return (if (lock) runs.locked(id) else runs.findById(id)).orElse(null)?.takeIf { it.ownerId == actor.id || canReview(actor, it) }
            ?: throw NotFoundException("Workflow run unavailable")
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    fun blocked(actor: User,run: WorkflowRun): String? {
        if (runCatching { readableDesigns(actor) }.isFailure) return "Workflow design access is unavailable. Contact an administrator."
        if (runtime.graph(run).nodes.any { it.kind in setOf(BlockKind.DOCUMENT, BlockKind.EMAIL) } && runCatching { documentSupport(actor) }.isFailure) return "The Document generator plugin or its PDF access is unavailable. Enable it to resume this workflow."
        val definition=definitions.findById(run.workflowId).orElse(null)
        return if (definition==null || definition.state!=WorkflowState.READY || (!definition.published && !(com.starter.access.Roles.admin(actor.role) && controls?.allows("admin.workflows") != false))) "This workflow is no longer published or ready. Contact an administrator." else null
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    fun active(actor: User,run: WorkflowRun) {blocked(actor,run)?.let {throw ForbiddenException(it)}}
    fun allowedDocuments(run: WorkflowRun): Set<UUID> {
        val revealed=runtime.history(run).map { it.nodeId }.toSet()+runtime.execution(run).active
        return runtime.graph(run).nodes.filter { it.id in revealed && it.kind in setOf(BlockKind.DOCUMENT,BlockKind.EMAIL) }.mapNotNull { runtime.effective(run,it).documentId }.toSet()
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    fun document(email: String,runId: UUID,id: UUID): GeneratedDocument {
        val actor=actor(email);documentSupport(actor);val run=owned(actor,runId);active(actor,run)
        if (id !in allowedDocuments(run)) throw NotFoundException("This document is not available at this workflow step")
        return documents.findById(id).orElseThrow {NotFoundException("Workflow document unavailable")}
    }
}
