package com.starter.notification

import com.starter.plugins.workflows.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.Optional

class NotificationWorkflowTest {
    private val f=NotificationFixture()
    private val runs=mock(WorkflowRunRepository::class.java)
    private val definitions=mock(WorkflowRepository::class.java)
    private val definition=WorkflowDefinition(state=WorkflowState.READY,published=true)
    private val row=WorkflowRun(ownerId=f.member.id,workflowId=definition.id,name="Member process",graphJson=f.mapper.writeValueAsString(WorkflowGraph(listOf(WorkflowBlock("start",BlockKind.START,"Read the instructions")),emptyList())),currentNodeId="start")
    private val listener=WorkflowNotificationListener(runs,definitions,WorkflowRuntime(f.mapper),f.users,f.publisher,mock(com.starter.plugins.BundledModuleAccess::class.java),"https://app.example.test")
    init {`when`(runs.findById(row.id)).thenReturn(Optional.of(row));`when`(definitions.findById(definition.id)).thenReturn(Optional.of(definition))}
    @Test fun `workflow alerts are optional disabled by default and use the private run owners preferences`() {
        listener.handle(WorkflowNotificationEvent(row.id,0,"workflow.started"))
        assertThat(f.sms.sent).isEmpty();assertThat(f.emailMessages).isEmpty()
        f.configure("workflow.started",NotificationRule(allowedMethods=setOf(f.sms.channel),defaultMethods=setOf(f.sms.channel)))
        listener.handle(WorkflowNotificationEvent(row.id,0,"workflow.started"))
        assertThat(f.sms.sent.single().recipient).isEqualTo(f.member.contactNumber)
        assertThat(f.sms.sent.single().text).contains("Member process","Read the instructions","https://app.example.test/workflows?run=${row.id}")
    }
    @Test fun `stale notifications unpublished definitions inactive owners and member opt outs do not send`() {
        f.configure("workflow.started",NotificationRule(allowedMethods=setOf(f.sms.channel),defaultMethods=setOf(f.sms.channel)))
        listener.handle(WorkflowNotificationEvent(row.id,1,"workflow.started"))
        definition.published=false;listener.handle(WorkflowNotificationEvent(row.id,0,"workflow.started"))
        definition.published=true;f.member.active=false;listener.handle(WorkflowNotificationEvent(row.id,0,"workflow.started"))
        f.member.active=true;f.preferences.save(f.member.email,NotificationPreferenceRequest(choices=mapOf("workflow.started" to emptySet())))
        listener.handle(WorkflowNotificationEvent(row.id,0,"workflow.started"))
        assertThat(f.sms.sent).isEmpty()
    }
    @Test fun `workflow notifications run after commit and provider failures cannot change workflow progress`() {
        val method=WorkflowNotificationListener::class.java.getMethod("handle",WorkflowNotificationEvent::class.java)
        assertThat(method.getAnnotation(TransactionalEventListener::class.java).phase).isEqualTo(TransactionPhase.AFTER_COMMIT)
        val publisher=mock(NotificationPublisher::class.java) {throw RuntimeException("Provider offline")}
        WorkflowNotificationListener(runs,definitions,WorkflowRuntime(f.mapper),f.users,publisher,mock(com.starter.plugins.BundledModuleAccess::class.java),"").handle(WorkflowNotificationEvent(row.id,0,"workflow.started"))
        assertThat(row.currentNodeId).isEqualTo("start");assertThat(row.status).isEqualTo(WorkflowRunStatus.IN_PROGRESS)
    }
}
