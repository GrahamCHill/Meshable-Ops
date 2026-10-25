package com.starter.plugins.knowledge

import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class KnowledgePlugin : WorkspacePlugin {
    override val manifest = PluginManifest(
        "knowledge", "Knowledge base", "1.0.0", "Linked Markdown documentation with images, Mermaid diagrams and permission-controlled PDF books.",
        compatibleVersions = listOf("1.0.0"), categories = setOf("Productivity"), hasAdminSettings = false,
        extensions = setOf(PluginExtension.PAGE, PluginExtension.API, PluginExtension.DATABASE),
        runtimes = setOf(PluginRuntime.REACT, PluginRuntime.KOTLIN, PluginRuntime.PYTHON),
        menuItems = listOf(PluginMenuItem("settings", "Export permissions & cover", setOf("ADMIN"))),
        tables = listOf(PluginTable(PAGES, "Shared documentation pages"), PluginTable(SETTINGS, "Export permissions and cover", setOf("ADMIN"), setOf("ADMIN"))))

    companion object {
        const val ID = "knowledge"
        const val PAGES = "plugin_knowledge_pages"
        const val SETTINGS = "plugin_knowledge_settings"
    }
}
