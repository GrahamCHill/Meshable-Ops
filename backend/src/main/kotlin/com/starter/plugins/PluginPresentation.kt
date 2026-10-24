package com.starter.plugins

/** Default presentation for shipped modules. Authors can override every field in their manifest. */
object PluginPresentation {
    fun defaults(value: PluginManifest): PluginManifest {
        val category = when {
            value.id.startsWith("calendar") -> "Calendars"
            value.id in setOf("github", "gitlab", "gitea") -> "Development"
            value.id in setOf("discord", "telegram", "teams", "whatsapp") -> "Communications"
            value.id in setOf("sharepoint", "onedrive") -> "Files"
            else -> "Productivity"
        }
        val admin = when (category) { "Communications" -> "communications"; "Files" -> "storage-sharing"; "Development" -> "development"; else -> "calendars" }
        val requires = if (value.id.startsWith("calendar-")) setOf("calendar") else emptySet()
        val optional = when (value.id) {
            "calendar" -> setOf("kanban", "calendar-google", "calendar-microsoft", "calendar-caldav")
            "kanban" -> setOf("github", "gitlab", "gitea", "calendar")
            "calendar-microsoft" -> setOf("sharepoint", "onedrive")
            else -> emptySet()
        }
        return value.copy(categories = value.categories.ifEmpty { setOf(category) }, icon = value.icon ?: "builtin", banner = value.banner ?: "builtin",
            requires = value.requires + requires, extends = value.extends + optional,
            adminMenu = value.adminMenu ?: if (value.hasAdminSettings && value.settingsFields.isNotEmpty()) PluginAdminMenu(admin, if (admin == "storage-sharing") "Storage & sharing" else category) else null)
    }
}
