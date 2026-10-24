package com.starter.navigation

import com.starter.access.AccessControlService
import com.starter.access.Roles
import com.starter.model.postgres.User
import com.starter.plugins.*
import com.starter.plugins.packages.InstalledPluginCatalogue
import org.springframework.stereotype.Component

@Component
class NavigationCatalogue(private val modules: List<WorkspacePlugin>, private val packages: InstalledPluginCatalogue,
    private val registry: PluginRegistry, private val access: AccessControlService) {
    fun all(): List<NavigationItem> {
        val core = listOf(NavigationItem("home", "Home", "/"), NavigationItem("dashboards", "Dashboards", "/dashboards"),
            NavigationItem("storage", "Storage", "/storage"), NavigationItem("projects", "Projects", "/projects"),
            NavigationItem("events", "Events", "/events"), NavigationItem("search", "Search workspace", "/search"))
        val manifests = (modules.map { it.manifest } + packages.manifests()).distinctBy { it.id }
            .filter { PluginExtension.PAGE in it.extensions }.sortedBy { it.name.lowercase() }
        val pages = manifests.map { manifest ->
            val view = modules.find { it.manifest.id == manifest.id }?.accessViews?.firstOrNull { !it.admin }
            NavigationItem("plugin:${manifest.id}", manifest.navigationLabel, view?.path ?: "/plugins/${manifest.id}", "PLUGIN",
                view?.id ?: "plugin.${manifest.id}", manifest.id, children = manifest.menuItems.map {
                    NavigationChild(it.path, it.label, "/plugins/${manifest.id}/${it.path}", it.roles)
                })
        }
        val links = manifests.flatMap { manifest -> manifest.placements.filter { it.slot == "navigation.links" }.map { item ->
            NavigationItem("extension:${manifest.id}:${item.section}", item.label,
                "/plugins/${manifest.id}" + if (item.section.isEmpty()) "" else "/${item.section}", "EXTENSION",
                pluginId = manifest.id, section = item.section)
        } }
        return core + pages + links
    }

    /** Member responses omit unavailable plugins and forbidden sections, even when their order is retained. */
    fun visible(actor: User): List<NavigationItem> {
        val features = access.features(actor)
        val available = registry.available(actor.role).associateBy { it.manifest.id }
        val role = Roles.plugin(actor.role)
        return all().filter { item ->
            when (item.kind) {
                "CORE" -> item.feature in features
                "PLUGIN" -> item.pluginId in available && item.feature in features
                else -> available[item.pluginId]?.manifest?.let { manifest ->
                    item.section.isEmpty() || manifest.menuItems.any { it.path == item.section && role in it.roles }
                } == true
            }
        }.map { it.copy(children = it.children.filter { child -> role in child.roles }) }
    }
}
