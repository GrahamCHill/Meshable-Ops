package com.starter.access

import com.starter.plugins.WorkspacePlugin
import com.starter.plugins.PluginExtension
import org.springframework.stereotype.Component

@Component
class AccessCatalogue(private val plugins: List<WorkspacePlugin>, private val packages: com.starter.plugins.packages.InstalledPluginCatalogue? = null) {
    fun maximum(role: String) = features.filter { if (Roles.admin(role)) it.id !in setOf("admin.access", "admin.security") else !it.admin }.map { it.id }.toSet()
    val features get() = listOf(
        AccessFeature("home", "Home", "/"), AccessFeature("dashboards", "Dashboards", "/dashboards"),
        AccessFeature("storage", "Storage", "/storage"), AccessFeature("documents", "Documents", "/documents"),
        AccessFeature("workflows", "Workflows", "/workflows"), AccessFeature("events", "Events", "/events"),
        AccessFeature("search", "Search and AI assistant", "/search"),
        AccessFeature("admin.branding", "Branding", "/admin/branding", true),
        AccessFeature("admin.plugins", "Plugin administration", "/admin/plugins", true),
        AccessFeature("admin.users", "User management", "/admin/users", true),
        AccessFeature("admin.storage", "Storage administration", "/admin/storage", true),
        AccessFeature("admin.documents", "Document download controls", "/admin/documents", true),
        AccessFeature("admin.notifications", "Notifications and email", "/admin/notifications", true),
        AccessFeature("admin.shares", "File sharing", "/admin/shares", true),
        AccessFeature("admin.webhooks", "Webhooks", "/admin/webhooks", true),
        AccessFeature("admin.workflows", "Workflow builder", "/admin/workflows", true),
        AccessFeature("admin.templates", "Document template editor", "/admin/document-templates", true),
        AccessFeature("admin.ai", "AI configuration", "/admin/ai", true),
        AccessFeature("admin.superset", "Superset authoring", "/admin/superset-graph-builder", true),
        AccessFeature("admin.dashboards", "Dashboard links", "/admin/dashboard-links", true),
        AccessFeature("admin.system", "System health", "/admin/system", true),
        AccessFeature("admin.audit", "Audit and backup", "/admin/audit", true),
        AccessFeature("admin.access", "Access controls", "/admin/access-controls", true),
        AccessFeature("admin.security", "Extension security", "/admin/security", true),
    ) + (plugins.map { it.manifest } + (packages?.manifests() ?: emptyList())).map { AccessFeature("plugin.${it.id}", it.name, "/plugins/${it.id}") }

    fun defaults(role: String): Set<String> = features.filter {
        when (role) {
            Roles.SUPER_ADMIN -> true
            "ADMIN" -> it.id !in setOf("admin.access", "admin.security")
            "USER" -> !it.admin
            "EXTERNAL" -> it.id == "storage"
            else -> false
        }
    }.map { it.id }.toSet()
}
