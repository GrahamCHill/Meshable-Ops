package com.starter.plugins.workflows

import com.starter.access.AccessFeature
import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class WorkflowsPlugin : WorkspacePlugin {
    override val accessFeature = "workflows"
    override val accessViews = listOf(AccessFeature("workflows", "Workflows", "/workflows"),
        AccessFeature("admin.workflows", "Workflow builder", "/admin/workflows", true))
    override val retainedTables = setOf("workflow_runs", "workflow_definitions", "workflow_action_bindings", "workflow_action_requests", "workflow_webhook_receipts")
    override val manifest = PluginManifest("workflows", "Workflows", "1.1.0",
        "Connect plugin actions to visual workflows with approvals, audit rules, restricted code modules, and configurable exports. Add Documents for PDF and email steps.",
        extensions = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE, PluginExtension.NOTIFICATIONS),
        tables = listOf(PluginTable("workflow_runs", "Member workflow runs"),
            PluginTable("workflow_definitions", "Workflow designs", writeRoles = setOf("ADMIN")),
            PluginTable("workflow_action_bindings", "Action workflow policies", readRoles=setOf("ADMIN"),writeRoles=setOf("ADMIN")),
            PluginTable("workflow_action_requests", "Workflow action authorizations",readRoles=setOf("ADMIN"),writeRoles=setOf("ADMIN")),
            PluginTable("workflow_webhook_receipts","Authenticated workflow webhook receipts",readRoles=setOf("ADMIN"),writeRoles=setOf("ADMIN"))),
        capabilities = mapOf("workflows.runs" to "Guided workflow runs", "workflows.actions" to "Server-enforced action workflows"), extends = setOf("documents"),
        features = listOf(PluginFeature("document-steps", "PDF attachments and email steps", setOf("documents.pdf"))),
        categories = setOf("Automation", "Productivity"), hasAdminSettings = false, compatibleVersions = listOf("1.0.0", "1.1.0"),
        runtimes = setOf(PluginRuntime.REACT, PluginRuntime.KOTLIN))
}
