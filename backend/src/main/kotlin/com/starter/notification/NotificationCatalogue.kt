package com.starter.notification

import com.starter.exception.NotFoundException
import com.starter.plugins.PluginExtension
import com.starter.plugins.PluginRegistry
import org.springframework.stereotype.Component

@Component
class NotificationCatalogue(private val providers: List<NotificationDefinitionProvider>,private val plugins: PluginRegistry) {
    private val common=setOf("applicationName","recipientName","subject","message")
    private val workflow=common+setOf("workflowName","stepLabel","status","link")
    private val core=listOf(
        NotificationDefinition("general.message","General messages","Messages sent by administrators or application features.",NotificationCategory.GENERAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("security.otp","Verification codes","One-time codes sent by a verified authentication flow. Member opt-outs do not apply.",NotificationCategory.SECURITY,common+setOf("code","expiresMinutes"),"Your verification code","Your verification code is {{code}}. It expires in {{expiresMinutes}} minutes. If you did not request it, ignore this message."),
        NotificationDefinition("documents.email","Document emails","Explicit sends of generated PDF attachments.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("shares.email","Protected file sharing","Explicit sends of password-protected PDF attachments and share links.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("workflow.email","Workflow email steps","Explicit PDF sends from an email step. Disabling prevents sending; manual delivery confirmation remains available.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("projects.report","Project report emails","Requested or scheduled internal PDF reports. The project lead is always included when sending.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("projects.digest","Personal work digest","Requested or opted-in scheduled email with the recipient's work and private calendar commitments.",NotificationCategory.TRANSACTIONAL,common,"{{subject}}","{{message}}"),
        NotificationDefinition("workflow.started","Workflow started","Notify the member who started a workflow.",NotificationCategory.WORKFLOW,workflow,"Workflow started: {{workflowName}}","{{workflowName}} has started. Next step: {{stepLabel}}. {{link}}",false),
        NotificationDefinition("workflow.step-ready","Workflow step ready","Notify the run owner when their next step becomes available.",NotificationCategory.WORKFLOW,workflow,"Next step: {{workflowName}}","Your next step in {{workflowName}} is {{stepLabel}}. {{link}}",false),
        NotificationDefinition("workflow.completed","Workflow completed","Notify the member when they finish their workflow.",NotificationCategory.WORKFLOW,workflow,"Workflow completed: {{workflowName}}","You have completed {{workflowName}}. {{link}}",false),
        NotificationDefinition("workflow.cancelled","Workflow cancelled","Notify the member when they cancel their workflow.",NotificationCategory.WORKFLOW,workflow,"Workflow cancelled: {{workflowName}}","Your run of {{workflowName}} has been cancelled. {{link}}",false))
    private val definitions: List<NotificationDefinition>
    init {
        val extra=providers.flatMap { provider ->
            require(PluginExtension.NOTIFICATIONS in plugins.definition(provider.pluginId).extensions)
            provider.definitions().map { definition ->
                require(definition.id.startsWith("${provider.pluginId}.")) {"Plugin notification IDs must use their plugin namespace"}
                definition.copy(pluginId=provider.pluginId)
            }
        }
        definitions=core+extra
        require(definitions.size<=200 && definitions.map {it.id}.distinct().size==definitions.size)
        definitions.forEach {require(it.id.matches(Regex("[a-z][a-z0-9.-]{1,99}")) && it.label.isNotBlank() && it.label.length<=100 && it.description.length<=500 && it.variables.size<=50 && it.variables.all {key -> key.matches(Regex("[a-z][A-Za-z0-9_.-]{0,79}"))})}
    }
    fun all()=definitions
    fun get(id: String)=definitions.find {it.id==id} ?: throw NotFoundException("Notification type unavailable")
    fun active(definition: NotificationDefinition)=definition.pluginId?.let {runCatching {plugins.requireAvailable(it,"ADMIN")}.isSuccess} ?: true
}
