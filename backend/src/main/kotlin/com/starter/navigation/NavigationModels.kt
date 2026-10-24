package com.starter.navigation

data class NavigationPreferences(val order: List<String> = emptyList(), val submenus: Map<String, List<String>> = emptyMap(), val revision: Long = -1)
data class NavigationChild(val id: String, val label: String, val path: String, val roles: Set<String> = setOf("ADMIN", "USER"))
data class NavigationItem(val id: String, val label: String, val path: String, val kind: String = "CORE",
    val feature: String = id, val pluginId: String? = null, val section: String = "", val children: List<NavigationChild> = emptyList())
data class NavigationView(val preferences: NavigationPreferences, val items: List<NavigationItem>)
