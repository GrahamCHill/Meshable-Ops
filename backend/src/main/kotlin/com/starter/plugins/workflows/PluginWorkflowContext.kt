package com.starter.plugins.workflows

import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** Named, method-specific contracts for business actions; ordinary read/list routes never trigger them. */
@Component
class PluginWorkflowContext : WorkflowActionContextProvider {
    private data class Action(val descriptor: WorkflowActionDescriptor, val pattern: Regex)
    private fun action(id: String, label: String, plugin: String, method: String, path: String, external: Boolean = false,
        fields: List<String> = emptyList(), outputs: List<String> = emptyList()) = Action(WorkflowActionDescriptor(id,label,plugin,external,method,path,fields,outputs),
        Regex(path.replace(Regex("\\{[^}]+}"),"[^/]+")))
    private val reportFields = listOf("trigger.resource.id","trigger.input.kind","trigger.input.from","trigger.input.to","trigger.input.boardId","trigger.input.sendEmail")
    private val exportFields = listOf("trigger.resource.projectId","trigger.resource.contextId","trigger.resource.kind","trigger.resource.format","trigger.input.from","trigger.input.to","trigger.input.projectId","trigger.input.title")
    private val definitions = listOf(
        action("projects.report.generate","Generate project report","projects","POST","/api/projects/{id}/reports",true,reportFields,
            listOf("export.from","export.to","export.report.kind","export.report.title","export.report.includeDescriptions","export.report.includeAiSummary")),
        action("projects.report.export","Export project report PDF","projects","GET","/api/projects/{id}/reports/{reportId}/pdf",true,reportFields),
        action("projects.report.schedule","Schedule project reports","projects","POST","/api/projects/{id}/schedules",fields=reportFields),
        action("knowledge.export","Export knowledge book","knowledge","POST","/api/plugins/knowledge/exports",true,exportFields,listOf("export.knowledge.title")),
        action("knowledge.page.create","Create knowledge page","knowledge","POST","/api/plugins/knowledge/pages"),
        action("knowledge.page.update","Update knowledge page","knowledge","PUT","/api/plugins/knowledge/pages/{id}"),
        action("knowledge.page.delete","Delete knowledge page","knowledge","DELETE","/api/plugins/knowledge/pages/{id}"),
        action("timesheets.export","Export approved timesheets","timesheets","GET","/api/plugins/timesheets/export",true,exportFields,listOf("export.from","export.to")),
        action("timesheets.create","Create time entry","timesheets","POST","/api/plugins/timesheets"),
        action("timesheets.review","Submit or review time entry","timesheets","POST","/api/plugins/timesheets/{id}/action",fields=listOf("trigger.input.action","trigger.resource.id")),
        action("billing.generate","Generate billing document","billing","POST","/api/plugins/billing",fields=listOf("trigger.input.projectId","trigger.input.from","trigger.input.to")),
        action("billing.send","Send billing document","billing","POST","/api/plugins/billing/{id}/send",true),
        action("billing.export","Export billing PDF","billing","GET","/api/plugins/billing/{id}/pdf",true),
        action("billing.state","Change billing document state","billing","POST","/api/plugins/billing/{id}/state",fields=listOf("trigger.input.state","trigger.resource.id")),
        action("documents.generate","Generate document","documents","POST","/api/documents",fields=listOf("trigger.input.templateId","trigger.input.name")),
        action("documents.export","Download document PDF","documents","GET","/api/documents/{id}/download",true, listOf("trigger.resource.id")),
        action("documents.email","Email document","documents","POST","/api/documents/{id}/email",true,listOf("trigger.resource.id","trigger.input.recipient","trigger.input.subject")),
        action("calendar.create","Create calendar event","calendar","POST","/api/plugins/calendar/events"),
        action("calendar.update","Update calendar event","calendar","PUT","/api/plugins/calendar/events/{id}"),
        action("calendar.delete","Delete calendar event","calendar","DELETE","/api/plugins/calendar/events/{id}"),
        action("calendar.export","Export calendar","calendar","GET","/api/plugins/calendar/export",true,listOf("trigger.input.from","trigger.input.until","trigger.input.zone")),
        action("calendar.publish","Publish event to connected provider","calendar","POST","/api/plugins/calendar/events/{id}/publish/{provider}",true,listOf("trigger.resource.id","trigger.resource.provider")),
        action("jira.link","Link Jira project","jira","POST","/api/plugins/jira/projects/{projectId}/links",fields=listOf("trigger.resource.projectId")),
        action("jira.sync","Refresh Jira project metrics","jira","POST","/api/plugins/jira/projects/{projectId}/links/{id}/sync",fields=listOf("trigger.resource.projectId","trigger.resource.id")),
        action("figma.link","Link Figma file","figma","POST","/api/plugins/figma/projects/{projectId}/files",fields=listOf("trigger.resource.projectId")),
        action("figma.refresh","Refresh Figma file","figma","POST","/api/plugins/figma/projects/{projectId}/files/{id}/refresh",fields=listOf("trigger.resource.projectId","trigger.resource.id")),
        action("timecamp.sync","Sync TimeCamp entries","timecamp","POST","/api/plugins/timecamp/sync"),
        action("cloud-costs.refresh","Refresh cloud costs","cloud-costs","POST","/api/plugins/cloud-costs/connections/{id}/refresh"),
        action("on-call.action","Respond to on-call alert","on-call","POST","/api/plugins/on-call/alerts/{id}/action",fields=listOf("trigger.input.action","trigger.resource.id"))
    ) + listOf("google-ads","meta-ads","google-analytics","meta-page-analytics","cloudflare-analytics").flatMap { plugin -> listOf(
        action("$plugin.export","Export analytics CSV or PDF",plugin,"GET","/api/plugins/$plugin/contexts/{kind}/{contextId}/links/export/{format}",true,exportFields,listOf("export.format")),
        action("$plugin.sync","Refresh analytics snapshot",plugin,"POST","/api/plugins/$plugin/contexts/{kind}/{contextId}/links/{id}/sync")
    ) }
    override val actions = definitions.map { it.descriptor }
    override fun supports(path: String) = definitions.any { it.pattern.matches(path) }
    override fun actionsFor(method: String, path: String) = definitions.filter { it.descriptor.method==method && it.pattern.matches(path) }.map { it.descriptor }
    override fun context(email: String, path: String, body: Any?): WorkflowTriggerContext {
        val request=(RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return WorkflowTriggerContext()
        return WorkflowTriggerContext(actionsFor(request.method,path).map { it.id }.toSet())
    }
}
