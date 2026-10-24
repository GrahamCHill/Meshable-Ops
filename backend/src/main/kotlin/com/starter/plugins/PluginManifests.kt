package com.starter.plugins

import com.starter.exception.BadRequestException

internal object PluginManifests {
    private val roles = setOf("ADMIN", "USER")
    fun validateIdentity(manifest: PluginManifest) {
        require(manifest.id.matches(Regex("[a-z][a-z0-9-]{1,39}")) && manifest.apiVersion in 1..100 && manifest.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Invalid plugin manifest" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 100 && manifest.navigationLabel.isNotBlank() && manifest.navigationLabel.length <= 60 && manifest.description.length <= 500) { "Invalid plugin metadata" }
    }
    fun validate(manifest: PluginManifest) {
        require(manifest.compatibleVersions.size <= 50 && manifest.compatibleVersions.all { PluginVersionRules.rule(it) != null }) { "compatibleVersions must contain up to 50 exact SemVer releases, inclusive version ranges, or comparator ranges" }
        val ids = manifest.requires + manifest.extends + manifest.incompatibleWith.keys
        require(ids.size <= 50 && ids.all { it.matches(Regex("[a-z][a-z0-9-]{1,39}")) && it != manifest.id }) { "Use other plugin IDs for relationships" }
        require(manifest.incompatibleWith.values.all { it.isNotBlank() && it.length <= 300 }) { "Explain plugin incompatibilities" }
        require(manifest.categories.size <= 5 && manifest.categories.all { it.isNotBlank() && it.length <= 50 && it.none(Char::isISOControl) }) { "Use up to five short categories" }
        listOfNotNull(manifest.icon, manifest.banner).forEach { require(it == "builtin" || it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_./-]{0,119}")) && it.split('/').none { segment -> segment in setOf("", ".", "..") }) { "Use packaged image paths" } }
        manifest.adminMenu?.let { require(it.category.matches(Regex("[a-z][a-z0-9-]{1,59}")) && it.categoryLabel.length <= 80 && it.label.length <= 100 && listOf(it.categoryLabel,it.label).none { value -> value.any(Char::isISOControl) }) { "Use a valid admin category and labels" } }
        require(!manifest.providesSms || PluginExtension.NOTIFICATIONS in manifest.extensions) { "SMS providers must declare notifications" }
        require(manifest.placements.size <= 30 && manifest.placements.map { it.slot to it.section }.distinct().size == manifest.placements.size)
        manifest.placements.forEach { require(it.slot.matches(Regex("[a-z][a-z0-9.-]{1,79}")) && it.label.isNotBlank() && it.label.length <= 100 && (it.section.isBlank() || manifest.menuItems.any { menu -> menu.path == it.section })) { "Use named extension slots and declared submenu pages" } }
        require(manifest.placements.none { it.mode == PluginPlacementMode.PANEL && it.slot in setOf("header.actions", "navigation.links", "account.menu") }) { "Compact navigation slots support links only" }
        require(manifest.placements.isEmpty() || PluginExtension.PAGE in manifest.extensions) { "Screen placements require a page" }
        require(setOf("CLIENT", "PROJECT").containsAll(manifest.contextTargets)) { "Context targets must be CLIENT or PROJECT" }
        require(manifest.contextTargets.isEmpty() || PluginExtension.PAGE in manifest.extensions) { "Context extensions require a page" }
        val capabilities = manifest.capabilities.keys + manifest.requiresCapabilities + manifest.features.flatMap { it.requires }
        require(capabilities.all { it.matches(Regex("[a-z][a-z0-9.-]{1,79}")) } && manifest.capabilities.values.all { it.isNotBlank() && it.length <= 100 }) { "Invalid plugin capabilities" }
        require(manifest.requiresCapabilities.intersect(manifest.capabilities.keys).isEmpty()) { "A plugin cannot supply its own requirement" }
        require(manifest.features.size <= 30 && manifest.features.map { it.id }.distinct().size == manifest.features.size)
        manifest.features.forEach { require(it.id.matches(Regex("[a-z][a-z0-9-]{1,39}")) && it.label.isNotBlank() && it.label.length <= 100 && it.requires.isNotEmpty()) }
        require("ADMIN" in manifest.supportedRoles && roles.containsAll(manifest.supportedRoles)) { "Invalid supported plugin roles" }
        require("ADMIN" in manifest.defaultRoles && manifest.supportedRoles.containsAll(manifest.defaultRoles)) { "Invalid default plugin roles" }
        require(manifest.settingsFields.size <= 30 && manifest.settingsFields.map { it.key }.distinct().size == manifest.settingsFields.size)
        manifest.settingsFields.forEach {
            require(it.key.matches(Regex("[a-z][A-Za-z0-9]{0,39}")) && it.label.isNotBlank() && it.label.length <= 100 && it.help.length <= 600 && it.maxLength in 1..4096) { "Invalid plugin settings field" }
        }
        require(manifest.settingsFields.isEmpty() || PluginExtension.SETTINGS in manifest.extensions)
        require(manifest.menuItems.size <= 20 && manifest.menuItems.map { it.path }.distinct().size == manifest.menuItems.size)
        manifest.menuItems.forEach {
            require(it.path.matches(Regex("[a-z][a-z0-9-]{0,39}")) && it.label.isNotBlank() && it.label.length <= 60 && it.roles.isNotEmpty() && roles.containsAll(it.roles)) { "Invalid plugin submenu" }
        }
        require(manifest.tables.size <= 20 && manifest.tables.map { it.name }.distinct().size == manifest.tables.size)
        val prefix = "plugin_${manifest.id.replace('-', '_')}_"
        manifest.tables.forEach {
            require(it.name.startsWith(prefix) && it.name.length <= 63 && it.name.matches(Regex("[a-z][a-z0-9_]+"))) { "Plugin tables must use their own namespace" }
            require(it.label.isNotBlank() && it.label.length <= 100 && valid(it.readRoles, it.writeRoles)) { "Invalid plugin table policy" }
        }
        require(manifest.tables.isEmpty() || PluginExtension.DATABASE in manifest.extensions)
        require(PluginExtension.SEARCH !in manifest.extensions || manifest.tables.isNotEmpty())
    }
    fun validateSettings(manifest: PluginManifest, request: PluginSettingsRequest) {
        if (!manifest.supportedRoles.containsAll(request.allowedRoles)) throw BadRequestException("This plugin supports administrator access only")
        if (request.searchEnabled && PluginExtension.SEARCH !in manifest.extensions) throw BadRequestException("This plugin has no search provider")
        if (request.tableAccess.keys.any { key -> manifest.tables.none { it.name == key } } || request.tableAccess.values.any { !valid(it.readRoles, it.writeRoles) })
            throw BadRequestException("Choose valid read/write roles for declared plugin tables; administrators must retain access and writers must be readers")
    }
    private fun valid(read: Set<String>, write: Set<String>) = "ADMIN" in read && "ADMIN" in write && roles.containsAll(read) && read.containsAll(write)
}
