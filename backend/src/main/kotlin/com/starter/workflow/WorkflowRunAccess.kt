package com.starter.workflow

import com.starter.documents.*
import com.starter.exception.*
import com.starter.model.postgres.User
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class WorkflowRunAccess(private val runs: WorkflowRunRepository,private val definitions: WorkflowRepository,
    private val access: DocumentAccess,private val documents: GeneratedDocumentRepository,private val runtime: WorkflowRuntime, private val controls: com.starter.access.AccessControlService? = null) {
    fun actor(email: String) = access.actor(email)
    fun owned(actor: User,id: UUID,lock: Boolean = false) = (if (lock) runs.locked(id) else runs.findById(id)).orElse(null)
        ?.takeIf { it.ownerId==actor.id } ?: throw NotFoundException("Workflow run unavailable")
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    fun blocked(actor: User,run: WorkflowRun): String? {
        val definition=definitions.findById(run.workflowId).orElse(null)
        return if (definition==null || definition.state!=WorkflowState.READY || (!definition.published && !(com.starter.access.Roles.admin(actor.role) && controls?.allows("admin.workflows") != false))) "This workflow is no longer published or ready. Contact an administrator." else null
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    fun active(actor: User,run: WorkflowRun) {blocked(actor,run)?.let {throw ForbiddenException(it)}}
    fun allowedDocuments(run: WorkflowRun): Set<UUID> {
        val revealed=runtime.history(run).map { it.nodeId }.toSet()+listOfNotNull(run.currentNodeId)
        return runtime.graph(run).nodes.filter { it.id in revealed && it.kind in setOf(BlockKind.DOCUMENT,BlockKind.EMAIL) }.mapNotNull { runtime.effective(run,it).documentId }.toSet()
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    fun document(email: String,runId: UUID,id: UUID): GeneratedDocument {
        val actor=actor(email);val run=owned(actor,runId);active(actor,run)
        if (id !in allowedDocuments(run)) throw NotFoundException("This document is not available at this workflow step")
        return documents.findById(id).orElseThrow {NotFoundException("Workflow document unavailable")}
    }
}
