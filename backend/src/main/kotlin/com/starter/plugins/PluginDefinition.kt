package com.starter.plugins

enum class PluginExtension { PAGE, API, DATABASE, SEARCH, SETTINGS }

enum class PluginFieldType { TEXT, SECRET }
data class PluginSettingField(val key: String, val label: String, val type: PluginFieldType = PluginFieldType.TEXT,
    val required: Boolean = false, val help: String = "", val maxLength: Int = 200)

data class PluginMenuItem(val path: String, val label: String, val roles: Set<String> = setOf("ADMIN", "USER"))
data class PluginTable(val name: String, val label: String,
    val readRoles: Set<String> = setOf("ADMIN", "USER"), val writeRoles: Set<String> = setOf("ADMIN", "USER"))
data class PluginTableAccess(val readRoles: Set<String>, val writeRoles: Set<String>)

data class PluginManifest(val id: String, val name: String, val version: String, val description: String,
    val navigationLabel: String = name, val apiVersion: Int = 1,
    val extensions: Set<PluginExtension> = setOf(PluginExtension.PAGE, PluginExtension.API),
    val menuItems: List<PluginMenuItem> = emptyList(), val tables: List<PluginTable> = emptyList(),
    val settingsFields: List<PluginSettingField> = emptyList(), val supportedRoles: Set<String> = setOf("ADMIN", "USER"))

/** Implement as a Spring bean in a reviewed, compiled-in plugin module. */
interface WorkspacePlugin {
    val manifest: PluginManifest
    fun validateConfiguration(values: Map<String, String>) = Unit
}

data class PluginView(val manifest: PluginManifest, val enabled: Boolean, val allowedRoles: Set<String>,
    val searchEnabled: Boolean = false, val tableAccess: Map<String, PluginTableAccess> = emptyMap())
data class PluginSettingsRequest(val enabled: Boolean, val allowedRoles: Set<String> = setOf("ADMIN", "USER"),
    val searchEnabled: Boolean = false, val tableAccess: Map<String, PluginTableAccess> = emptyMap())

data class PluginClearRequest(val confirmation: String)
data class PluginClearResult(val deletedRows: Map<String, Int>)
