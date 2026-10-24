package com.starter.plugins.workflows

import com.starter.access.AccessFeature
import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class WorkflowsPlugin : WorkspacePlugin {
    override val accessFeature = "workflows"
    override val accessViews = listOf(AccessFeature("workflows", "Workflows", "/workflows"),
        AccessFeature("admin.workflows", "Workflow builder", "/admin/workflows", true))
    override val retainedTables = setOf("workflow_runs", "workflow_definitions")
    override val manifest = PluginManifest("workflows", "Workflows", "1.0.0",
        "Build visual processes and let members follow published workflows with saved progress. Add Documents for PDF and email steps.",
        extensions = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE, PluginExtension.NOTIFICATIONS),
        tables = listOf(PluginTable("workflow_runs", "Member workflow runs"),
            PluginTable("workflow_definitions", "Workflow designs", writeRoles = setOf("ADMIN"))),
        capabilities = mapOf("workflows.runs" to "Guided workflow runs"), extends = setOf("documents"),
        features = listOf(PluginFeature("document-steps", "PDF attachments and email steps", setOf("documents.pdf"))),
        categories = setOf("Automation", "Productivity"), hasAdminSettings = false, compatibleVersions = listOf("1.0.0"),
        runtimes = setOf(PluginRuntime.REACT, PluginRuntime.KOTLIN))
}
