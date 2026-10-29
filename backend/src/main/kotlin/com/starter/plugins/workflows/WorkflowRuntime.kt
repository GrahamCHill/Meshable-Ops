package com.starter.plugins.workflows

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class WorkflowExecution(val active: Set<String> = emptySet(),val completed: Set<String> = emptySet(),
    val arrivals: Map<String,Boolean> = emptyMap(),val signals: Map<String,Boolean> = emptyMap())

@Component
class WorkflowRuntime(private val mapper: ObjectMapper) {
    fun graph(run: WorkflowRun) = mapper.readValue(run.graphJson,WorkflowGraph::class.java)
    fun history(run: WorkflowRun): List<WorkflowVisit> = mapper.readValue(run.historyJson,object:TypeReference<List<WorkflowVisit>>() {})
    fun values(run: WorkflowRun): Map<String,String> = mapper.readValue(run.valuesJson,object:TypeReference<Map<String,String>>() {})
    fun overrides(run: WorkflowRun): Map<UUID,UUID> = mapper.readValue(run.documentOverridesJson,object:TypeReference<Map<UUID,UUID>>() {})
    fun execution(run: WorkflowRun): WorkflowExecution = if (run.executionJson=="{}") WorkflowExecution(
        active=setOfNotNull(run.currentNodeId),completed=history(run).map {it.nodeId}.toSet()) else mapper.readValue(run.executionJson,WorkflowExecution::class.java)
    fun initialize(run: WorkflowRun) {
        val roots=graph(run).nodes.filter {it.kind in setOf(BlockKind.START,BlockKind.WEBHOOK)}.map {it.id}.toSet()
        saveExecution(run,WorkflowExecution(active=roots,signals=roots.associateWith {true}))
    }
    fun active(run: WorkflowRun) = graph(run).nodes.filter {it.id in execution(run).active}
    fun block(run: WorkflowRun,nodeId: String) = active(run).find {it.id==nodeId} ?: throw ConflictException("This step is not ready or has already completed")
    fun current(run: WorkflowRun) = block(run,run.currentNodeId ?: throw ConflictException("This workflow is waiting for inputs"))
    fun effective(run: WorkflowRun,block: WorkflowBlock) = block.copy(documentId=block.documentId?.let { overrides(run)[it] ?: it })
    fun matches(run: WorkflowRun,revision: Long,nodeId: String? = null) {
        if (run.revision!=revision) throw ConflictException("This run changed. Reload before continuing.")
        if (run.status!=WorkflowRunStatus.IN_PROGRESS) throw ConflictException("This workflow run has finished")
        if (nodeId!=null) block(run,nodeId)
    }
    private fun saveExecution(run: WorkflowRun,state: WorkflowExecution) {
        run.executionJson=mapper.writeValueAsString(state)
        val nodes=graph(run).nodes
        run.currentNodeId=run.currentNodeId?.takeIf {it in state.active}
            ?: nodes.firstOrNull {it.id in state.active && it.kind!=BlockKind.WEBHOOK}?.id
            ?: nodes.firstOrNull {it.id in state.active}?.id
    }
    fun waiting(run: WorkflowRun): List<WorkflowWaitingNode> {
        if(run.status!=WorkflowRunStatus.IN_PROGRESS)return emptyList()
        val graph=graph(run);val state=execution(run)
        return graph.nodes.mapNotNull {node ->
            if(node.kind==BlockKind.WEBHOOK && node.id in state.active) WorkflowWaitingNode(node.id,node.label,node.kind,0,1,endpointId=node.webhookEndpointId,event=node.webhookEvent)
            else if(node.kind==BlockKind.JOIN && node.id !in state.active && node.id !in state.completed) {
                val inputs=graph.edges.filter {it.target==node.id};val arrived=inputs.count {it.id in state.arrivals}
                if(arrived==0)null else WorkflowWaitingNode(node.id,node.label,node.kind,arrived,inputs.size,
                    inputs.filter {it.id !in state.arrivals}.map {edge -> graph.nodes.first {it.id==edge.source}.label})
            } else null
        }
    }
    fun emailBusy(run: WorkflowRun) = run.emailState==WorkflowEmailState.SENDING && (run.emailAttemptAt?.isAfter(Instant.now().minusSeconds(120)) != false)
    fun advance(run: WorkflowRun,request: WorkflowAdvanceRequest, actorId: UUID? = null) {
        matches(run,request.revision,request.nodeId)
        val block=block(run,request.nodeId)
        if (block.kind==BlockKind.WEBHOOK) throw ForbiddenException("Only an authenticated webhook can signal this trigger")
        if ((block.auditRequired || block.kind == BlockKind.AUDIT) && request.note.isBlank()) throw BadRequestException("An audit note is required for this step")
        if (emailBusy(run)) throw ConflictException("Email delivery is still in progress. Check its result before continuing.")
        val branch=when(block.kind) {
            BlockKind.CONDITION -> {
                if (request.action!=WorkflowAction.EVALUATE) throw BadRequestException("Evaluate the condition to choose its next step")
                val trusted = block.conditionSource == ConditionSource.VALUE || (block.field.startsWith("trigger.") || block.field.startsWith("webhook."))
                val value = if (trusted) values(run)[block.field].orEmpty() else request.value
                if (!trusted) run.valuesJson=mapper.writeValueAsString(values(run) + (block.field to value))
                if (evaluate(block,value)) Branch.TRUE else Branch.FALSE
            }
            BlockKind.JOIN -> {
                if(request.action!=WorkflowAction.CONTINUE)throw BadRequestException("Release the joined inputs to continue")
                when(block.joinRule) { JoinRule.PASS -> Branch.NEXT;else -> if(execution(run).signals[block.id]!=false) Branch.TRUE else Branch.FALSE }
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
        val outcome=when(block.kind) {BlockKind.CONDITION -> branch.name;BlockKind.REVIEW -> if(request.action==WorkflowAction.REJECT) "REJECTED" else "REVIEWED";BlockKind.JOIN -> "JOIN_${branch.name}";BlockKind.CODE -> "CODE_EXECUTED";BlockKind.AUDIT -> "AUDIT_RECORDED";BlockKind.EMAIL -> "DELIVERY_CONFIRMED_MANUALLY";else -> "COMPLETED"}
        finishStep(run,outcome,request.note,branch,actorId,nodeId=block.id)
    }
    fun signalWebhook(run: WorkflowRun,nodeId: String,signal: Boolean,note: String) {
        if(run.status!=WorkflowRunStatus.IN_PROGRESS || block(run,nodeId).kind!=BlockKind.WEBHOOK) throw ConflictException("This webhook trigger is not waiting")
        finishStep(run,"WEBHOOK_RECEIVED",note,nodeId=nodeId,signal=signal)
    }
    fun finishStep(run: WorkflowRun,outcome: String,note: String = "",branch: Branch = Branch.NEXT,actorId: UUID? = null,
        nodeId: String? = null,signal: Boolean? = null) {
        val block=block(run,nodeId ?: run.currentNodeId ?: throw ConflictException("No active step"))
        val graph=graph(run);val state=execution(run)
        val recorded=if(block.kind==BlockKind.END && block.endOutcome==WorkflowEndOutcome.DENY) "REJECTED" else outcome
        run.historyJson=mapper.writeValueAsString(history(run)+WorkflowVisit(block.id,block.label,block.kind,recorded,note,Instant.now(),actorId,true))
        val completed=state.completed+block.id;val active=state.active.toMutableSet().apply {remove(block.id)}
        val arrivals=state.arrivals.toMutableMap();val signals=state.signals.toMutableMap()
        if(recorded=="REJECTED") {run.status=WorkflowRunStatus.REJECTED;active.clear()}
        else {
            val outgoing=graph.edges.filter {it.source==block.id && it.branch==branch}
            if(block.kind!=BlockKind.END && outgoing.isEmpty()) throw ConflictException("This saved workflow has no next step")
            val value=signal ?: when(branch) {Branch.TRUE->true;Branch.FALSE->false;Branch.NEXT->state.signals[block.id] ?: true}
            outgoing.forEach {edge -> arrivals[edge.id]=value
                val target=graph.nodes.first {it.id==edge.target}
                if(target.id !in completed && target.id !in active) {
                    val inputs=graph.edges.filter {it.target==target.id}
                    val ready=target.kind!=BlockKind.JOIN || target.joinInputs==JoinInputs.ANY || inputs.all {it.id in arrivals}
                    if(ready) {
                        active+=target.id
                        val incoming=inputs.mapNotNull {arrivals[it.id]}
                        signals[target.id]=if(target.kind==BlockKind.JOIN) when(target.joinRule) {
                            JoinRule.ALL_TRUE->incoming.all {it};JoinRule.ANY_TRUE->incoming.any {it};JoinRule.PASS->true
                        } else value
                    }
                }
            }
            val partialJoin=graph.nodes.any {node -> node.kind==BlockKind.JOIN && node.id !in completed &&
                graph.edges.any {it.target==node.id && it.id in arrivals}}
            if(active.isEmpty() && !partialJoin)run.status=WorkflowRunStatus.COMPLETED
        }
        saveExecution(run,WorkflowExecution(active,completed,arrivals,signals))
        if(run.status!=WorkflowRunStatus.IN_PROGRESS)run.currentNodeId=null
        if(block.kind==BlockKind.EMAIL || run.status!=WorkflowRunStatus.IN_PROGRESS) {run.emailState=WorkflowEmailState.NONE;run.emailAttemptId=null;run.emailAttemptAt=null}
        run.updatedAt=Instant.now()
        if(run.status==WorkflowRunStatus.IN_PROGRESS)active(run).firstOrNull {it.kind==BlockKind.JOIN && !it.auditRequired}?.let {join ->
            val result=execution(run).signals[join.id]!=false
            val output=if(join.joinRule==JoinRule.PASS)Branch.NEXT else if(result)Branch.TRUE else Branch.FALSE
            finishStep(run,"JOIN_${output.name}",if(join.joinInputs==JoinInputs.ALL)"All connected inputs received" else "First connected input received",output,actorId,nodeId=join.id)
        }
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
