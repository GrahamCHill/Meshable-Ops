package com.starter.plugins.workflows

import com.starter.plugins.documents.*
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.notification.*
import com.starter.service.DocumentAccessService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class WorkflowEmailClaim(val ownerId: UUID,val attemptId: UUID,val block: WorkflowBlock,val auditNote: String = "",val actorEmail: String? = null)

/** Commit the claim before any external send so timeouts/crashes never trigger an automatic resend. */
@Service
class WorkflowEmailClaims(private val runs: WorkflowRunRepository,private val guard: WorkflowRunAccess,private val runtime: WorkflowRuntime,private val alerts: com.starter.plugins.workflows.WorkflowNotificationHooks,
    private val audit: com.starter.service.AuditLogService? = null) {
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun claim(email: String,id: UUID,request: WorkflowSendRequest): WorkflowEmailClaim {
        val actor=guard.actor(email);val run=guard.owned(actor,id,true);guard.active(actor,run);runtime.matches(run,request.revision,request.nodeId)
        val block=runtime.effective(run,runtime.current(run))
        if (block.kind!=BlockKind.EMAIL || block.documentId==null) throw BadRequestException("This is not an email step")
        if (block.auditRequired && request.note.isBlank()) throw BadRequestException("An audit note is required for this email step")
        if (run.emailState!=WorkflowEmailState.NONE) throw ConflictException("Delivery was already attempted. Check its result before confirming this step.")
        val attempt=UUID.randomUUID()
        run.emailState=WorkflowEmailState.SENDING;run.emailAttemptId=attempt;run.emailAttemptAt=Instant.now();run.updatedAt=Instant.now()
        runs.saveAndFlush(run)
        audit?.record(email,"WORKFLOW_EMAIL_CLAIMED","WORKFLOW_RUN",run.id.toString())
        return WorkflowEmailClaim(actor.id,attempt,block,request.note,email)
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun finish(id: UUID,claim: WorkflowEmailClaim,accepted: Boolean) {
        val run=runs.locked(id).orElse(null) ?: return
        if (run.ownerId!=claim.ownerId || run.emailAttemptId!=claim.attemptId || run.emailState!=WorkflowEmailState.SENDING) return
        if (accepted) runtime.finishStep(run,"EMAIL_ACCEPTED",claim.auditNote.ifBlank {"The email provider accepted the PDF attachment; inbox delivery is not confirmed."},actorId=claim.ownerId)
        else {run.emailState=WorkflowEmailState.UNCERTAIN;run.updatedAt=Instant.now()}
        runs.saveAndFlush(run)
        audit?.record(claim.actorEmail,if(accepted) "WORKFLOW_EMAIL_ACCEPTED" else "WORKFLOW_EMAIL_UNCERTAIN","WORKFLOW_RUN",run.id.toString())
        if(accepted) alerts.advanced(run)
    }
}

@Service
class WorkflowEmailService(private val claims: WorkflowEmailClaims,private val runs: WorkflowRunService,private val guard: WorkflowRunAccess,
    private val delivery: GeneratedDocumentDelivery,private val notifications: NotificationService,private val policy: DocumentAccessService) {
    fun send(email: String,id: UUID,request: WorkflowSendRequest): WorkflowRunView {
        val view=runs.get(email,id)
        if (view.blockedReason!=null) throw ForbiddenException(view.blockedReason)
        val block=view.current ?: throw ConflictException("This run has finished")
        if (block.kind!=BlockKind.EMAIL) throw BadRequestException("This is not an email step")
        if (block.protection==DocumentEmailProtection.PASSWORD && (request.password.length<12 || request.password.toByteArray(Charsets.UTF_8).size>72 || request.password.any {it.isISOControl()})) throw BadRequestException("Use a PDF password of 12 characters or more, up to 72 UTF-8 bytes")
        if (!notifications.availableFor("workflow.email","email")) throw BadRequestException("Workflow email delivery is disabled or not configured. Contact an administrator or record delivery handled outside the app.")
        if (policy.current().downloadMode==DownloadMode.DISABLED) throw ForbiddenException("Document downloads and email attachments are disabled")
        guard.document(email,id,block.documentId ?: throw NotFoundException("Email document unavailable"))
        val claim=claims.claim(email,id,request)
        try {
            val result=delivery.email(email,EmailDocumentRequest(claim.block.recipient,claim.block.subject,claim.block.note.ifBlank {"Please find the document attached."},claim.block.protection,request.password),"workflow.email") {
                guard.document(email,id,claim.block.documentId!!)
            }
            if (result.status!="ACCEPTED") throw com.starter.email.EmailDeliveryException(result.message)
            claims.finish(id,claim,true)
        } catch (_:Exception) {
            claims.finish(id,claim,false)
            throw com.starter.email.EmailDeliveryException("Email delivery could not be confirmed. Check the outbox/provider before recording how this step was handled; the app will not resend automatically.")
        }
        return runs.get(email,id)
    }
}
