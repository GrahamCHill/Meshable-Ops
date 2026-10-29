package com.starter.plugins.workflows

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.*
import com.starter.repository.postgres.UserRepository
import com.starter.service.AuditLogService
import com.starter.webhook.*
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity @Table(name="workflow_webhook_receipts",uniqueConstraints=[UniqueConstraint(columnNames=["endpoint_id","delivery_key"])])
class WorkflowWebhookReceipt(@Id val id: UUID = UUID.randomUUID(),val endpointId: UUID = UUID.randomUUID(),
    @Column(length=128) val deliveryKey: String = "",val runId: UUID = UUID.randomUUID(),val createdAt: Instant = Instant.now())
interface WorkflowWebhookReceiptRepository : JpaRepository<WorkflowWebhookReceipt,UUID> {
    fun existsByEndpointIdAndDeliveryKey(endpointId: UUID,deliveryKey: String): Boolean
}

@Service
class WorkflowWebhookConfiguration(private val endpoints: WebhookEndpointRepository,private val users: UserRepository) {
    fun options() = endpoints.findAll().filter {it.direction==WebhookDirection.INCOMING && it.enabled}.map {mapOf("id" to it.id.toString(),"name" to it.name)}
    fun check(graph: WorkflowGraph) {
        graph.nodes.filter {it.kind==BlockKind.WEBHOOK}.forEach {node ->
            val endpoint=node.webhookEndpointId?.let {endpoints.findById(it).orElse(null)}
            if(endpoint?.direction!=WebhookDirection.INCOMING || !endpoint.enabled)throw BadRequestException("Choose an enabled incoming webhook endpoint")
            if((node.webhookStartsRun || node.webhookOwnerId!=null) && node.webhookOwnerId?.let {users.findById(it).orElse(null)?.takeIf {user -> user.active && user.role!="EXTERNAL"}}==null)
                throw BadRequestException("Choose an active internal account to own webhook-started runs")
        }
    }
}

/** Called only after the existing incoming endpoint authenticates its bearer/HMAC signature. */
@Service
class WorkflowWebhookTriggers(private val definitions: WorkflowRepository,private val runs: WorkflowRunRepository,
    private val runner: WorkflowRunService,private val runtime: WorkflowRuntime,private val guard: WorkflowRunAccess,
    private val users: UserRepository,private val receipts: WorkflowWebhookReceiptRepository,private val audit: AuditLogService,
    private val alerts: WorkflowNotificationHooks,private val mapper: com.fasterxml.jackson.databind.ObjectMapper) : IncomingWebhookExtension {
    @Transactional
    override fun receive(endpointId: UUID,deliveryId: String,payload: JsonNode) {
        val data=payload.path("data")
        // Other endpoint extensions can handle their own payloads independently.
        if(!data.has("workflowId") && !data.has("runId"))return
        if(!data.has("workflowId")) {
            val candidate=runCatching {UUID.fromString(data.path("runId").asText())}.getOrNull() ?: return
            if(!runs.findById(candidate).isPresent)return // An unrelated extension may also use a runId field.
        }
        if(!data.isObject)throw BadRequestException("Workflow webhook data must be an object")
        if(receipts.existsByEndpointIdAndDeliveryKey(endpointId,deliveryId))return
        fun uuid(field: String): UUID? = data.get(field)?.let {value ->
            if(!value.isTextual)throw BadRequestException("Use a workflow UUID in $field")
            runCatching {UUID.fromString(value.asText())}.getOrElse {throw BadRequestException("Use a workflow UUID in $field")}
        }
        val runId=uuid("runId");val requestedWorkflow=uuid("workflowId")
        val event=payload.path("event").takeIf {it.isTextual}?.asText() ?: throw BadRequestException("Choose a workflow webhook event")
        val reference=data.get("reference")?.let {
            if(!it.isTextual || it.asText().isBlank() || it.asText().length>120 || it.asText().any {char -> char.isISOControl()})throw BadRequestException("Use a printable business reference of up to 120 characters")
            it.asText().trim()
        }
        val sourceId=runId?.let {runs.findById(it).orElse(null)?.workflowId ?: throw NotFoundException("Workflow run unavailable")}
            ?: requestedWorkflow ?: throw BadRequestException("Provide workflowId and reference, or runId")
        if(requestedWorkflow!=null && requestedWorkflow!=sourceId)throw BadRequestException("Workflow and run IDs do not match")
        // Same lock order as reference-based manual creation: definition, then run. This also serializes duplicate deliveries.
        val source=definitions.locked(sourceId).orElse(null)?.takeIf {it.state==WorkflowState.READY && it.published}
            ?: throw NotFoundException("Published workflow unavailable")
        if(receipts.existsByEndpointIdAndDeliveryKey(endpointId,deliveryId))return
        val graph=mapper.readValue(source.graphJson,WorkflowGraph::class.java)
        val configured=graph.nodes.filter {it.kind==BlockKind.WEBHOOK && it.webhookEndpointId==endpointId && it.webhookEvent==event}
        val referenceOwner=if(runId==null) configured.mapNotNull {it.webhookOwnerId}.distinct().singleOrNull()
            ?: throw BadRequestException("Reference matching needs one configured internal run owner") else null
        var row=runId?.let {runs.locked(it).orElse(null)} ?: reference?.let {runs.findByWorkflowIdAndOwnerIdAndCorrelationKey(sourceId,referenceOwner!!,it).orElse(null)?.let {run -> runs.locked(run.id).orElse(null)}}
        if(row==null) {
            if(reference==null)throw BadRequestException("A webhook starting a run needs a unique business reference")
            val starters=configured.filter {it.webhookStartsRun}
            val ownerId=starters.mapNotNull {it.webhookOwnerId}.distinct().singleOrNull() ?: throw NotFoundException("No webhook start trigger matches this event")
            val owner=users.findById(ownerId).orElse(null)?.takeIf {it.active && it.role!="EXTERNAL"} ?: throw ForbiddenException("Workflow owner unavailable")
            val started=runner.start(owner.email,source.id,reference=reference)
            row=runs.locked(started.id).orElseThrow {ConflictException("Workflow run unavailable")}
        }
        val run=row!!
        if(reference!=null && run.correlationKey!=reference)throw BadRequestException("Business reference does not match the selected run")
        if(run.status!=WorkflowRunStatus.IN_PROGRESS)return
        val owner=guard.actor(users.findById(run.ownerId).orElse(null)?.email ?: throw ForbiddenException("Workflow owner unavailable"))
        guard.writable(owner);guard.active(owner,run)
        val nodes=runtime.active(run).filter {it.kind==BlockKind.WEBHOOK && it.webhookEndpointId==endpointId && it.webhookEvent==event}
        if(nodes.isEmpty())return // A new delivery ID cannot re-open an already satisfied trigger.
        val values=runtime.values(run).toMutableMap()
        nodes.forEach {node ->
            node.webhookFields.forEach {field -> data.get(field)?.let {value ->
                if(!value.isValueNode || value.isNull || value.asText().length>500)throw BadRequestException("Mapped webhook values must be non-null scalars of up to 500 characters")
                values["webhook.${node.id}.$field"]=value.asText()
            }}
            val signal=if(node.webhookBooleanField.isBlank())true else data.get(node.webhookBooleanField)?.takeIf {it.isBoolean}?.asBoolean()
                ?: throw BadRequestException("Webhook ${node.webhookBooleanField} must be a boolean")
            if(values.size>100 || values.entries.sumOf {it.key.length+it.value.length}>30000)throw BadRequestException("Workflow data limit exceeded")
            runtime.signalWebhook(run,node.id,signal,"Authenticated endpoint $endpointId; delivery $deliveryId; signal=$signal")
        }
        run.valuesJson=mapper.writeValueAsString(values)
        runs.saveAndFlush(run)
        receipts.saveAndFlush(WorkflowWebhookReceipt(endpointId=endpointId,deliveryKey=deliveryId,runId=run.id))
        audit.record(null,"WORKFLOW_WEBHOOK_RECEIVED","WORKFLOW_RUN",run.id.toString());alerts.advanced(run)
    }
}
