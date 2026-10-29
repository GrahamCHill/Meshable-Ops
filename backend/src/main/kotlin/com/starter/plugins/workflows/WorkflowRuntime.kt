package com.starter.plugins.workflows

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class WorkflowRuntime(private val mapper: ObjectMapper) {
    fun graph(run: WorkflowRun) = mapper.readValue(run.graphJson,WorkflowGraph::class.java)
    fun history(run: WorkflowRun): List<WorkflowVisit> = mapper.readValue(run.historyJson,object:TypeReference<List<WorkflowVisit>>() {})
    fun values(run: WorkflowRun): Map<String,String> = mapper.readValue(run.valuesJson,object:TypeReference<Map<String,String>>() {})
    fun overrides(run: WorkflowRun): Map<UUID,UUID> = mapper.readValue(run.documentOverridesJson,object:TypeReference<Map<UUID,UUID>>() {})
    fun current(run: WorkflowRun) = graph(run).nodes.find { it.id==run.currentNodeId } ?: throw ConflictException("This workflow is no longer on an active step")
    fun effective(run: WorkflowRun,block: WorkflowBlock) = block.copy(documentId=block.documentId?.let { overrides(run)[it] ?: it })
    fun matches(run: WorkflowRun,revision: Long,nodeId: String? = null) {
        if (run.revision!=revision || (nodeId!=null && run.currentNodeId!=nodeId)) throw ConflictException("This run changed. Reload before continuing.")
        if (run.status!=WorkflowRunStatus.IN_PROGRESS) throw ConflictException("This workflow run has finished")
    }
    fun emailBusy(run: WorkflowRun) = run.emailState==WorkflowEmailState.SENDING && (run.emailAttemptAt?.isAfter(Instant.now().minusSeconds(120)) != false)
    fun advance(run: WorkflowRun,request: WorkflowAdvanceRequest, actorId: UUID? = null) {
        matches(run,request.revision,request.nodeId)
        val block=current(run)
        if ((block.auditRequired || block.kind == BlockKind.AUDIT) && request.note.isBlank()) throw BadRequestException("An audit note is required for this step")
        if (emailBusy(run)) throw ConflictException("Email delivery is still in progress. Check its result before continuing.")
        val branch=when(block.kind) {
            BlockKind.CONDITION -> {
                if (request.action!=WorkflowAction.EVALUATE) throw BadRequestException("Evaluate the condition to choose its next step")
                val trusted = block.conditionSource == ConditionSource.VALUE || block.field.startsWith("trigger.")
                val value = if (trusted) values(run)[block.field].orEmpty() else request.value
                if (!trusted) run.valuesJson=mapper.writeValueAsString(values(run) + (block.field to value))
                if (evaluate(block,value)) Branch.TRUE else Branch.FALSE
            }
            BlockKind.REVIEW -> {
                if (request.action !in setOf(WorkflowAction.REVIEWED,WorkflowAction.REJECT)) throw BadRequestException("Confirm or reject this review before continuing")
                if (request.action == WorkflowAction.REJECT && request.note.isBlank()) throw BadRequestException("Record a reason when rejecting a review")
                if (!block.allowInitiatorReview && (actorId == null || actorId == run.ownerId)) throw ForbiddenException("A different assigned reviewer must approve this step")
                if (actorId != null && actorId != run.ownerId && actorId !in block.reviewerIds) throw ForbiddenException("This review is assigned to another member")
                Branch.NEXT
            }
            BlockKind.CODE -> {
                if (request.action != WorkflowAction.CONTINUE) throw BadRequestException("Run the code step to continue")
                val values = values(run) + WorkflowCode.execute(block.code, values(run))
                if (values.size > 100 || values.entries.sumOf { it.key.length + it.value.length } > 30000) throw BadRequestException("Workflow data limit exceeded")
                run.valuesJson = mapper.writeValueAsString(values)
                Branch.NEXT
            }
            BlockKind.EMAIL -> {if (request.action!=WorkflowAction.CONFIRM_EMAIL || request.note.isBlank()) throw BadRequestException("Send the email here, or confirm how delivery was handled outside the app");Branch.NEXT}
            else -> {if (request.action!=WorkflowAction.CONTINUE) throw BadRequestException("Continue from this step");Branch.NEXT}
        }
        val outcome=when(block.kind) {BlockKind.CONDITION -> branch.name;BlockKind.REVIEW -> if(request.action==WorkflowAction.REJECT) "REJECTED" else "REVIEWED";BlockKind.CODE -> "CODE_EXECUTED";BlockKind.AUDIT -> "AUDIT_RECORDED";BlockKind.EMAIL -> "DELIVERY_CONFIRMED_MANUALLY";else -> "COMPLETED"}
        finishStep(run,outcome,request.note,branch,actorId)
    }
    fun finishStep(run: WorkflowRun,outcome: String,note: String = "",branch: Branch = Branch.NEXT,actorId: UUID? = null) {
        val block=current(run)
        val recorded=if(block.kind==BlockKind.END && block.endOutcome==WorkflowEndOutcome.DENY) "REJECTED" else outcome
        run.historyJson=mapper.writeValueAsString(history(run)+WorkflowVisit(block.id,block.label,block.kind,recorded,note,Instant.now(),actorId,true))
        if (block.kind==BlockKind.END || recorded=="REJECTED") {run.currentNodeId=null;run.status=if(recorded=="REJECTED") WorkflowRunStatus.REJECTED else WorkflowRunStatus.COMPLETED}
        else run.currentNodeId=graph(run).edges.singleOrNull { it.source==block.id && it.branch==branch }?.target ?: throw ConflictException("This saved workflow has no next step")
        run.emailState=WorkflowEmailState.NONE;run.emailAttemptId=null;run.emailAttemptAt=null;run.updatedAt=Instant.now()
    }
    fun evaluate(block: WorkflowBlock,value: String): Boolean = when(block.operator) {
        ConditionOperator.EQUALS -> value.trim()==block.expected.trim()
        ConditionOperator.NOT_EQUALS -> value.trim()!=block.expected.trim()
        ConditionOperator.CONTAINS -> value.contains(block.expected)
        ConditionOperator.IS_SET -> value.isNotBlank()
        ConditionOperator.GREATER_THAN -> number(value)>number(block.expected)
    }
    private fun number(value: String): java.math.BigDecimal {
        if (!value.trim().matches(Regex("[+-]?[0-9]{1,30}(\\.[0-9]{1,12})?"))) throw BadRequestException("Enter a decimal number with at most 30 whole and 12 fractional digits")
        return value.trim().toBigDecimal()
    }
}
