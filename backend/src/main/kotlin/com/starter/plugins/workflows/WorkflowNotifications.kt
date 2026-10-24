package com.starter.plugins.workflows

import com.starter.notification.*
import org.springframework.stereotype.Component

@Component
class WorkflowNotifications : NotificationDefinitionProvider {
    override val pluginId = "workflows"
    override val namespace = "workflow."
    private val common = setOf("applicationName", "recipientName", "subject", "message")
    private val workflow = common + setOf("workflowName", "stepLabel", "status", "link")
    override fun definitions() = listOf(
        NotificationDefinition("workflow.email","Workflow email steps","Explicit PDF sends from an email step. Disabling prevents sending; manual delivery confirmation remains available.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("workflow.started","Workflow started","Notify the member who started a workflow.",NotificationCategory.WORKFLOW,workflow,"Workflow started: {{workflowName}}","{{workflowName}} has started. Next step: {{stepLabel}}. {{link}}",false),
        NotificationDefinition("workflow.step-ready","Workflow step ready","Notify the run owner when their next step becomes available.",NotificationCategory.WORKFLOW,workflow,"Next step: {{workflowName}}","Your next step in {{workflowName}} is {{stepLabel}}. {{link}}",false),
        NotificationDefinition("workflow.completed","Workflow completed","Notify the member when they finish their workflow.",NotificationCategory.WORKFLOW,workflow,"Workflow completed: {{workflowName}}","You have completed {{workflowName}}. {{link}}",false),
        NotificationDefinition("workflow.cancelled","Workflow cancelled","Notify the member when they cancel their workflow.",NotificationCategory.WORKFLOW,workflow,"Workflow cancelled: {{workflowName}}","Your run of {{workflowName}} has been cancelled. {{link}}",false)
    )
}
