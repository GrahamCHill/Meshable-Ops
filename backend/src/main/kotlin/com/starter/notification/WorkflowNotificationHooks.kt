package com.starter.notification

import com.starter.repository.postgres.UserRepository
import com.starter.workflow.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
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
@Configuration @EnableAsync
class NotificationExecution {
    @Bean("notificationExecutor") fun executor()=ThreadPoolTaskExecutor().apply {corePoolSize=2;maxPoolSize=2;queueCapacity=100;setThreadNamePrefix("notification-");setRejectedExecutionHandler(java.util.concurrent.ThreadPoolExecutor.DiscardPolicy())}
}
@Component
class WorkflowNotificationListener(private val runs: WorkflowRunRepository,private val definitions: WorkflowRepository,private val runtime: WorkflowRuntime,
    private val users: UserRepository,private val publisher: NotificationPublisher,@Value("\${app.public-base-url:}") private val baseUrl: String) {
    @Async("notificationExecutor") @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    fun handle(event: WorkflowNotificationEvent) {
        try {
            val run=runs.findById(event.runId).orElse(null) ?: return
            if(run.revision!=event.revision) return // A newer step supersedes this optional alert.
            val source=definitions.findById(run.workflowId).orElse(null) ?: return
            val actor=users.findById(run.ownerId).orElse(null)?.takeIf {it.active} ?: return
            if(source.state!=WorkflowState.READY || !source.published && !com.starter.access.Roles.admin(actor.role)) return
            val label=run.currentNodeId?.let {runtime.current(run).label}.orEmpty()
            publisher.publish(event.type,run.ownerId,mapOf("workflowName" to run.name,"stepLabel" to label,"status" to run.status.name,"link" to "${baseUrl.trimEnd('/')}/workflows?run=${run.id}"),"workflow:${run.id}:${event.revision}:${event.type}")
        } catch (_:Exception) {org.slf4j.LoggerFactory.getLogger(javaClass).warn("Optional workflow notification delivery was unavailable")}
    }
}
