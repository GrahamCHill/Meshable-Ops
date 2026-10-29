package com.starter.plugins.workflows

import com.starter.exception.*
import com.starter.repository.postgres.UserRepository
import com.starter.service.AuditLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkflowActionStore(private val bindings: WorkflowActionBindingRepository, private val requests: WorkflowActionRequestRepository,
    private val definitions: WorkflowRepository, private val runs: WorkflowRunRepository, private val workflows: WorkflowRunService,
    private val users: UserRepository, private val audit: AuditLogService,
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()) {
    /** Commit the prerequisite run before returning 428; the rejected business transaction cannot erase it. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun prepare(email: String, bindingId: UUID, fingerprint: String, context: Map<String,String>): WorkflowActionRequest {
        val binding=bindings.locked(bindingId)?.takeIf { it.enabled } ?: throw ConflictException("Workflow policy changed. Retry the action.")
        val actor=users.findByEmail(email).orElse(null)?.takeIf { it.active && it.role != "EXTERNAL" } ?: throw ForbiddenException("An active internal member is required")
        val source=definitions.findById(binding.workflowId).orElse(null)?.takeIf { it.published && it.state==WorkflowState.READY }
            ?: throw ForbiddenException("This action requires a workflow that is currently unavailable")
        if (mapper.readTree(source.graphJson).path("nodes").any { it.path("kind").asText()=="EMAIL" })
            throw ForbiddenException("This action requires a prerequisite design without email steps")
        val existing=requests.findByOwnerIdAndBindingIdAndFingerprintAndStateOrderByCreatedAtDesc(actor.id,binding.id,fingerprint,"PENDING")
        existing.forEach { row ->
            if (row.bindingRevision==binding.revision && row.workflowRevision==source.revision && row.expiresAt.isAfter(Instant.now()) &&
                runs.findById(row.runId).orElse(null)?.status != WorkflowRunStatus.CANCELLED) return row
            row.state="REVOKED"
        }
        val run=workflows.start(email,source.id,context)
        val row=requests.saveAndFlush(WorkflowActionRequest(ownerId=actor.id,bindingId=binding.id,bindingRevision=binding.revision,
            workflowRevision=source.revision,runId=run.id,fingerprint=fingerprint))
        audit.record(email,"WORKFLOW_ACTION_REQUIRED","WORKFLOW_ACTION",row.id.toString())
        return row
    }

    fun completed(email: String, row: WorkflowActionRequest): Boolean = workflows.get(email,row.runId).let {
        if(it.status==WorkflowRunStatus.REJECTED) throw ForbiddenException("The workflow rejected this action")
        it.status==WorkflowRunStatus.COMPLETED && it.blockedReason==null
    }

    /** Consume every prerequisite atomically before the final action. Never retry uncertain external sends. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun consume(email: String, approvals: List<WorkflowActionRequest>, fingerprint: String): Map<String,String> {
        val actor=users.findByEmail(email).orElse(null)?.takeIf { it.active } ?: throw ForbiddenException("Account unavailable")
        val outputs=mutableMapOf<String,String>()
        approvals.sortedBy { it.bindingId }.forEach { approval ->
            val binding=bindings.locked(approval.bindingId)?.takeIf { it.enabled && it.revision==approval.bindingRevision }
                ?: throw ConflictException("Workflow policy changed. Retry the action.")
            val row=requests.locked(approval.id) ?: throw ConflictException("Action approval unavailable")
            val definition=definitions.findById(binding.workflowId).orElse(null)
            val run=runs.findById(row.runId).orElse(null)
            if (row.ownerId!=actor.id || row.fingerprint!=fingerprint || row.state!="PENDING" || !row.expiresAt.isAfter(Instant.now()) ||
                definition?.published!=true || definition.state!=WorkflowState.READY || definition.revision!=row.workflowRevision ||
                run?.ownerId!=actor.id || run.status!=WorkflowRunStatus.COMPLETED) throw ConflictException("Complete a current workflow approval before retrying this exact action")
            val values: Map<String,String> = mapper.readValue(run.valuesJson,object:com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {})
            values.filterKeys { it.startsWith("export.") }.forEach { (key,value) ->
                if (outputs.containsKey(key) && outputs[key]!=value) throw ConflictException("Required workflows disagree on an export option: $key")
                outputs[key]=value
            }
            row.state="CONSUMED";row.consumedAt=Instant.now()
            audit.record(email,"WORKFLOW_ACTION_AUTHORIZED","WORKFLOW_ACTION",row.id.toString())
        }
        requests.flush()
        return outputs
    }
}
