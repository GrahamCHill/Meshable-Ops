package com.starter.plugins

enum class PluginExtension { PAGE, API, DATABASE, SEARCH, SETTINGS, NOTIFICATIONS }

enum class PluginFieldType { TEXT, SECRET }
data class PluginSettingField(val key: String, val label: String, val type: PluginFieldType = PluginFieldType.TEXT,
    val required: Boolean = false, val help: String = "", val maxLength: Int = 200)

data class PluginMenuItem(val path: String, val label: String, val roles: Set<String> = setOf("ADMIN", "USER"))
data class PluginTable(val name: String, val label: String,
    val readRoles: Set<String> = setOf("ADMIN", "USER"), val writeRoles: Set<String> = setOf("ADMIN", "USER"))
data class PluginTableAccess(val readRoles: Set<String>, val writeRoles: Set<String>)
data class PluginFeature(val id: String, val label: String, val requires: Set<String>)
data class PluginFeatureView(val id: String, val label: String, val available: Boolean, val providers: Set<String>, val missing: Set<String>)
data class PluginAdminMenu(val category: String, val categoryLabel: String = "", val label: String = "")
enum class PluginPlacementMode { LINK, PANEL }
data class PluginPlacement(val slot: String, val label: String, val section: String = "", val mode: PluginPlacementMode = PluginPlacementMode.LINK)

data class PluginManifest(val id: String, val name: String, val version: String, val description: String,
    val navigationLabel: String = name, val apiVersion: Int = 1,
    val extensions: Set<PluginExtension> = setOf(PluginExtension.PAGE, PluginExtension.API),
    val menuItems: List<PluginMenuItem> = emptyList(), val tables: List<PluginTable> = emptyList(),
    val settingsFields: List<PluginSettingField> = emptyList(), val supportedRoles: Set<String> = setOf("ADMIN", "USER"),
    val capabilities: Map<String, String> = emptyMap(), val requiresCapabilities: Set<String> = emptySet(),
    val features: List<PluginFeature> = emptyList(), val defaultRoles: Set<String> = supportedRoles,
    val contextTargets: Set<String> = emptySet(), val categories: Set<String> = emptySet(),
    val icon: String? = null, val banner: String? = null, val requires: Set<String> = emptySet(), val extends: Set<String> = emptySet(),
    val incompatibleWith: Map<String, String> = emptyMap(), val hasAdminSettings: Boolean = true,
    val adminMenu: PluginAdminMenu? = null, val providesSms: Boolean = false, val placements: List<PluginPlacement> = emptyList())

/** Implement as a Spring bean in a reviewed, compiled-in plugin module. */
interface WorkspacePlugin {
    val manifest: PluginManifest
    fun validateConfiguration(values: Map<String, String>) = Unit
    fun missingConfiguration(values: Map<String, String>) = manifest.settingsFields.filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
}

data class PluginView(val manifest: PluginManifest, val enabled: Boolean, val allowedRoles: Set<String>,
    val searchEnabled: Boolean = false, val tableAccess: Map<String, PluginTableAccess> = emptyMap(),
    val active: Boolean = enabled, val missingDependencies: Set<String> = emptySet(), val features: List<PluginFeatureView> = emptyList(),
    val source: String = "BUNDLED", val author: com.starter.plugins.packages.PluginAuthor? = null, val blockedBySecurity: Boolean = false,
    val compatibilityIssues: Set<String> = emptySet(), val removed: Boolean = false)
data class PluginSettingsRequest(val enabled: Boolean, val allowedRoles: Set<String> = setOf("ADMIN", "USER"),
    val searchEnabled: Boolean = false, val tableAccess: Map<String, PluginTableAccess> = emptyMap())

data class PluginClearRequest(val confirmation: String)
data class PluginClearResult(val deletedRows: Map<String, Int>)
