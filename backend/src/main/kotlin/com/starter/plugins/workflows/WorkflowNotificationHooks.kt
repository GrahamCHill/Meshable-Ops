package com.starter.plugins.workflows

import com.starter.notification.*

import com.starter.repository.postgres.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

data class WorkflowNotificationEvent(val runId: UUID,val revision: Long,val type: String)
@Component
class WorkflowNotificationHooks(private val events: ApplicationEventPublisher) {
    fun started(run: WorkflowRun)=events.publishEvent(WorkflowNotificationEvent(run.id,run.revision,"workflow.started"))
    fun advanced(run: WorkflowRun)=events.publishEvent(WorkflowNotificationEvent(run.id,run.revision,if(run.status==WorkflowRunStatus.COMPLETED) "workflow.completed" else "workflow.step-ready"))
    fun cancelled(run: WorkflowRun)=events.publishEvent(WorkflowNotificationEvent(run.id,run.revision,"workflow.cancelled"))
}
@Component
class WorkflowNotificationListener(private val runs: WorkflowRunRepository,private val definitions: WorkflowRepository,private val runtime: WorkflowRuntime,
    private val users: UserRepository,private val publisher: NotificationPublisher,private val modules: com.starter.plugins.BundledModuleAccess,@Value("\${app.public-base-url:}") private val baseUrl: String) {
    @Async("notificationExecutor") @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    fun handle(event: WorkflowNotificationEvent) {
        try {
            val run=runs.findById(event.runId).orElse(null) ?: return
            if(run.revision!=event.revision) return // A newer step supersedes this optional alert.
            val source=definitions.findById(run.workflowId).orElse(null) ?: return
            val actor=users.findById(run.ownerId).orElse(null)?.takeIf {it.active} ?: return
            modules.check("workflows", actor, "workflow_runs", "workflows")
            if(source.state!=WorkflowState.READY || !source.published && !com.starter.access.Roles.admin(actor.role)) return
            val label=run.currentNodeId?.let {runtime.current(run).label}.orEmpty()
            publisher.publish(event.type,run.ownerId,mapOf("workflowName" to run.name,"stepLabel" to label,"status" to run.status.name,"link" to "${baseUrl.trimEnd('/')}/workflows?run=${run.id}"),"workflow:${run.id}:${event.revision}:${event.type}")
        } catch (_:Exception) {org.slf4j.LoggerFactory.getLogger(javaClass).warn("Optional workflow notification delivery was unavailable")}
    }
}
