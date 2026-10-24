package com.starter.plugins.kanban

import com.starter.plugins.*
import org.springframework.stereotype.Component

@Component
class KanbanPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("kanban", "Kanban boards", "1.0.0",
        "Private task boards with lists, cards, and card movement.", navigationLabel = "Boards",
        extensions = PluginExtension.entries.toSet(),
        menuItems = listOf(PluginMenuItem("boards", "Saved boards"), PluginMenuItem("guide", "Board guide")),
        tables = listOf(PluginTable("plugin_kanban_boards", "Boards and cards")))
}
