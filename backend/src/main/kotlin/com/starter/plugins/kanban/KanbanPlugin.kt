package com.starter.plugins.kanban

import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class KanbanPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("kanban", "Kanban boards", "2.0.0",
        "Project boards, sprints, assignments, reviews, comments, and Git repository links.", navigationLabel = "Boards",
        compatibleVersions = listOf("1.0.0"), extensions = PluginExtension.entries.toSet(),
        menuItems = listOf(PluginMenuItem("boards", "Saved boards"), PluginMenuItem("guide", "Board guide")),
        tables = listOf(PluginTable("plugin_kanban_comments", "Card comments and mentions"), PluginTable("plugin_kanban_development_links", "Repository and PR links"), PluginTable("plugin_kanban_boards", "Boards and cards")),
        capabilities = mapOf("kanban.boards" to "Kanban boards"),
        features = listOf(PluginFeature("development-links", "Repository and pull request links", setOf("source.repositories")),
            PluginFeature("calendar-deadlines", "Card deadlines in Calendar", setOf("calendar.events"))), contextTargets = setOf("PROJECT"))
}
