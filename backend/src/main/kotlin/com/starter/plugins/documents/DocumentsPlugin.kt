package com.starter.plugins.documents

import com.starter.access.AccessFeature
import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class DocumentsPlugin : WorkspacePlugin {
    override val accessFeature = "documents"
    override val accessViews = listOf(AccessFeature("documents", "Documents", "/documents"),
        AccessFeature("admin.templates", "Document template editor", "/admin/document-templates", true))
    override val retainedTables = setOf("generated_documents", "document_templates")
    override val manifest = PluginManifest("documents", "Document generator", "1.0.0",
        "Design reusable WeasyCanvas templates, generate PDFs, and send plain or password-protected attachments.",
        navigationLabel = "Documents", extensions = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE, PluginExtension.NOTIFICATIONS),
        tables = listOf(PluginTable("generated_documents", "Generated PDFs"),
            PluginTable("document_templates", "Document templates", writeRoles = setOf("ADMIN"))),
        capabilities = mapOf("documents.pdf" to "Generated PDFs and templates"),
        categories = setOf("Documents", "Productivity"), hasAdminSettings = false, compatibleVersions = listOf("1.0.0"),
        runtimes = setOf(PluginRuntime.REACT, PluginRuntime.KOTLIN, PluginRuntime.PYTHON))
}
