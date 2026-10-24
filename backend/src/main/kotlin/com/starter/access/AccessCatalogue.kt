package com.starter.access

import com.starter.plugins.WorkspacePlugin
import com.starter.plugins.PluginExtension
import org.springframework.stereotype.Component

private const val ACCESS_CONTROLS = "admin.access"
private const val EXTENSION_SECURITY = "admin.security"

@Component
class AccessCatalogue(private val plugins: List<WorkspacePlugin>, private val packages: com.starter.plugins.packages.InstalledPluginCatalogue? = null) {
    fun maximum(role: String) = features.filter { if (Roles.admin(role)) it.id !in setOf(ACCESS_CONTROLS, EXTENSION_SECURITY) else !it.admin }.map { it.id }.toSet()
    val features get() = listOf(
        AccessFeature("home", "Home", "/"), AccessFeature("dashboards", "Dashboards", "/dashboards"),
        AccessFeature("storage", "Storage", "/storage"), AccessFeature("events", "Event log", "/admin/events", true),
        AccessFeature("search", "Search and AI assistant", "/search"),
        AccessFeature("projects", "Clients and projects", "/projects"),
        AccessFeature("admin.projects", "Project settings, sections, tags, and teams", "/admin/projects", true),
        AccessFeature("admin.branding", "Branding", "/admin/branding", true),
        AccessFeature("admin.navigation", "Menu ordering", "/admin/navigation", true),
        AccessFeature("admin.plugins", "Plugin administration", "/admin/plugins", true),
        AccessFeature("admin.users", "User management", "/admin/users", true),
        AccessFeature("admin.storage", "Storage administration", "/admin/storage", true),
        AccessFeature("admin.documents", "Document download controls", "/admin/documents", true),
        AccessFeature("admin.notifications", "Notifications and email", "/admin/notifications", true),
        AccessFeature("admin.shares", "File sharing", "/admin/shares", true),
        AccessFeature("admin.webhooks", "Webhooks", "/admin/webhooks", true),
        AccessFeature("admin.ai", "AI configuration", "/admin/ai", true),
        AccessFeature("admin.superset", "Superset authoring", "/admin/superset-graph-builder", true),
        AccessFeature("admin.dashboards", "Dashboard links", "/admin/dashboard-links", true),
        AccessFeature("admin.system", "System health", "/admin/system", true),
        AccessFeature("admin.audit", "Audit and backup", "/admin/audit", true),
        AccessFeature(ACCESS_CONTROLS, "Access controls", "/admin/access-controls", true),
        AccessFeature(EXTENSION_SECURITY, "Extension security", "/admin/security", true),
    ) + plugins.flatMap { it.accessViews } + (packages?.manifests() ?: emptyList()).map { AccessFeature("plugin.${it.id}", it.name, "/plugins/${it.id}") }

    fun defaults(role: String): Set<String> = features.filter {
        when (role) {
            Roles.SUPER_ADMIN -> true
            "ADMIN" -> it.id !in setOf(ACCESS_CONTROLS, EXTENSION_SECURITY)
            "USER" -> !it.admin
            "EXTERNAL" -> it.id == "storage"
            else -> false
        }
    }.map { it.id }.toSet()
}
