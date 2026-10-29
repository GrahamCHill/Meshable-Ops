package com.starter.plugins.workflows

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import com.starter.service.AuditLogService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*

class WorkflowWebhookTriggersTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules();private val runtime=WorkflowRuntime(mapper)
    private val definitions=mock(WorkflowRepository::class.java);private val rows=mock(WorkflowRunRepository::class.java)
    private val runner=mock(WorkflowRunService::class.java);private val guard=mock(WorkflowRunAccess::class.java)
    private val users=mock(UserRepository::class.java);private val receipts=mock(WorkflowWebhookReceiptRepository::class.java)
    private val owner=User(email="owner@example.test");private val endpoint=UUID.randomUUID()
    private val graph=WorkflowGraph(listOf(WorkflowBlock("manual",BlockKind.START,"Manual"),
        WorkflowBlock("hook",BlockKind.WEBHOOK,"Webhook",webhookEndpointId=endpoint,webhookEvent="order.ready",webhookStartsRun=true,webhookOwnerId=owner.id,webhookFields=listOf("status"),webhookBooleanField="success"),
        WorkflowBlock("join",BlockKind.JOIN,"Both"),WorkflowBlock("end",BlockKind.END,"End")),
        listOf(WorkflowEdge("manual-join","manual","join"),WorkflowEdge("hook-join","hook","join"),WorkflowEdge("join-end","join","end")))
    private val definition=WorkflowDefinition(state=WorkflowState.READY,published=true,graphJson=mapper.writeValueAsString(graph))
    private val records=mutableMapOf<UUID,WorkflowRun>();private val delivered=mutableSetOf<String>()
    private val service=WorkflowWebhookTriggers(definitions,rows,runner,runtime,guard,users,receipts,mock(AuditLogService::class.java),mock(WorkflowNotificationHooks::class.java),mapper)
    init {
        `when`(definitions.locked(definition.id)).thenReturn(Optional.of(definition))
        `when`(users.findById(owner.id)).thenReturn(Optional.of(owner));`when`(guard.actor(owner.email)).thenReturn(owner)
        `when`(rows.findById(any(UUID::class.java) ?: UUID.randomUUID())).thenAnswer {Optional.ofNullable(records[it.arguments[0]])}
        `when`(rows.locked(any(UUID::class.java) ?: UUID.randomUUID())).thenAnswer {Optional.ofNullable(records[it.arguments[0]])}
        `when`(rows.findByWorkflowIdAndOwnerIdAndCorrelationKey(eq(definition.id) ?: definition.id,eq(owner.id) ?: owner.id,anyString())).thenAnswer {call -> Optional.ofNullable(records.values.find {it.correlationKey==call.arguments[2] && it.ownerId==call.arguments[1]})}
        `when`(receipts.existsByEndpointIdAndDeliveryKey(eq(endpoint) ?: endpoint,anyString())).thenAnswer {it.arguments[1] in delivered}
        `when`(receipts.saveAndFlush(any(WorkflowWebhookReceipt::class.java))).thenAnswer {call -> (call.arguments[0] as WorkflowWebhookReceipt).also {delivered+=it.deliveryKey}}
        `when`(runner.start(eq(owner.email) ?: owner.email,eq(definition.id) ?: definition.id,anyMap(),anyString())).thenAnswer {call ->
            val run=newRun(call.arguments[3] as String)
            WorkflowRunView(run.id,"Workflow",run.workflowId,0,run.status,0,runtime.current(run),emptyList(),emptyMap(),emptyList(),null,run.emailState,null)
        }
    }
    private fun newRun(reference: String)=WorkflowRun(ownerId=owner.id,workflowId=definition.id,graphJson=definition.graphJson,correlationKey=reference).also {runtime.initialize(it);records[it.id]=it}
    private fun payload(data: String)=mapper.readTree("""{"event":"order.ready","data":$data}""")
    @Test fun `a referenced manual run receives its webhook once and both inputs release the join`() {
        val run=newRun("ORDER-123");runtime.advance(run,WorkflowAdvanceRequest(0,"manual"))
        val data="""{"workflowId":"${definition.id}","reference":"ORDER-123","status":"ready","success":true,"password":"not copied"}"""
        service.receive(endpoint,"delivery-1",payload(data));service.receive(endpoint,"delivery-1",payload(data))
        assertThat(runtime.execution(run).active).containsExactly("end")
        assertThat(runtime.history(run).count {it.kind==BlockKind.WEBHOOK}).isEqualTo(1)
        assertThat(runtime.values(run)).containsEntry("webhook.hook.status","ready").doesNotContainKey("webhook.hook.password")
        verify(runner,never()).start(anyString(),any(UUID::class.java) ?: UUID.randomUUID(),anyMap(),anyString())
    }
    @Test fun `webhook can create a correlated run first and manual completion later releases the same join`() {
        val data="""{"workflowId":"${definition.id}","reference":"ORDER-456","success":true}"""
        service.receive(endpoint,"delivery-2",payload(data))
        val run=records.values.single()
        assertThat(run.ownerId).isEqualTo(owner.id);assertThat(run.correlationKey).isEqualTo("ORDER-456")
        assertThat(runtime.execution(run).active).containsExactly("manual")
        runtime.advance(run,WorkflowAdvanceRequest(0,"manual"))
        assertThat(runtime.execution(run).active).containsExactly("end")
    }
    @Test fun `run ID delivery validates booleans and a different endpoint cannot satisfy the waiting trigger`() {
        val run=newRun("ORDER-789")
        assertThatThrownBy {service.receive(endpoint,"bad",payload("""{"runId":"${run.id}","success":"true"}"""))}.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        service.receive(UUID.randomUUID(),"wrong-endpoint",payload("""{"runId":"${run.id}","success":true}"""))
        assertThat(runtime.execution(run).active).contains("hook")
        service.receive(endpoint,"correct",payload("""{"runId":"${run.id}","success":true}"""))
        assertThat(runtime.execution(run).active).doesNotContain("hook")
    }
}
