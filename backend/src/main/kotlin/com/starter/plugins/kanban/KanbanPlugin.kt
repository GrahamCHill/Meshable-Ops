package com.starter.plugins.kanban

import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class KanbanPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("kanban", "Kanban boards", "1.6.0",
        "Project boards, sprints, assignments, reviews, comments, and Git repository links.", navigationLabel = "Boards",
        compatibleVersions = listOf("1.0.0", "1.1.0"), extensions = PluginExtension.entries.toSet(),
        menuItems = listOf(PluginMenuItem("boards", "Saved boards"), PluginMenuItem("guide", "Board guide")),
        tables = listOf(PluginTable("plugin_kanban_retrospectives", "Sprint retrospectives"), PluginTable("plugin_kanban_comments", "Card comments and mentions"), PluginTable("plugin_kanban_development_links", "Repository and PR links"), PluginTable("plugin_kanban_boards", "Boards and cards"), PluginTable("plugin_kanban_estimation", "Project estimation preferences")),
        settingsFields = listOf(PluginSettingField("storyPoints", "Enable story points", PluginFieldType.BOOLEAN, help = "Organisation default. Administrators can override this per project in these settings.", defaultValue = "true"),
            PluginSettingField("tshirtSizes", "Enable T-shirt sizes", PluginFieldType.BOOLEAN, help = "Use XS, S, M, L, XL and XXL estimates. Can be used alongside story points.", defaultValue = "false"),
            PluginSettingField("applicationUrl", "Workspace URL for notification links", help = "Public HTTPS address members use to open this workspace. Blank uses the server's APP_PUBLIC_BASE_URL. Needed for clickable reply links in external messages.", maxLength = 1000)),
        capabilities = mapOf("kanban.boards" to "Kanban boards"),
        features = listOf(PluginFeature("development-links", "Repository and pull request links", setOf("source.repositories")),
            PluginFeature("calendar-deadlines", "Card deadlines in Calendar", setOf("calendar.events"))), contextTargets = setOf("PROJECT"))
    override fun validateConfiguration(values: Map<String,String>) { values["applicationUrl"]?.let { com.starter.plugins.integrations.IntegrationHttpClient.endpoint(it) } }
}
