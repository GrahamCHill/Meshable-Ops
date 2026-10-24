package com.starter.plugins

import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.starter.exception.ForbiddenException

@Service
class PluginRegistry(definitions: List<WorkspacePlugin>, private val settings: PluginSettingsRepository) {
    private val modules = definitions.associateBy { it.manifest.id }
    private val manifests: Map<String, PluginManifest>
    init {
        val all = definitions.map { it.manifest }
        require(all.map { it.id }.distinct().size == all.size) { "Plugin IDs must be unique" }
        all.forEach {
            require(it.id.matches(Regex("[a-z][a-z0-9-]{1,39}")) && it.apiVersion == 1 && it.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Invalid or incompatible plugin manifest" }
            require(it.name.isNotBlank() && it.name.length <= 100 && it.navigationLabel.isNotBlank() && it.navigationLabel.length <= 60 && it.description.length <= 500) { "Invalid plugin metadata" }
            PluginManifests.validate(it)
        }
        manifests = all.associateBy { it.id }
    }
    @Transactional(readOnly = true)
    fun installed(): List<PluginView> {
        val saved = settings.findAll().associateBy { it.pluginId }
        return manifests.values.sortedBy { it.name }.map { view(it, saved[it.id]) }
    }
    fun available(role: String) = installed().filter { it.enabled && role in it.allowedRoles }

    fun definition(id: String) = manifests[id] ?: throw NotFoundException("Plugin not installed")
    fun validateConfiguration(id: String, values: Map<String, String>) = modules[id]?.validateConfiguration(values)
        ?: throw NotFoundException("Plugin not installed")

    fun requireTable(id: String, table: String, role: String, write: Boolean = false) {
        requireAvailable(id, role)
        val policy = view(definition(id), settings.findById(id).orElse(null)).tableAccess[table]
            ?: throw NotFoundException("Plugin table not declared")
        if (role !in (if (write) policy.writeRoles else policy.readRoles)) throw ForbiddenException("Your role cannot ${if (write) "change" else "read"} this plugin table")
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun requireAvailable(id: String, role: String) {
        val manifest = manifests[id] ?: throw NotFoundException("Plugin unavailable")
        val view = view(manifest, settings.findById(id).orElse(null))
        if (!view.enabled || role !in view.allowedRoles) throw NotFoundException("Plugin unavailable")
    }

    @Transactional
    fun configure(id: String, request: PluginSettingsRequest): PluginView {
        val manifest = manifests[id] ?: throw NotFoundException("Plugin not installed")
        if ("ADMIN" !in request.allowedRoles || request.allowedRoles.any { it !in setOf("ADMIN", "USER") }) throw BadRequestException("Choose administrators or all signed-in users")
        PluginManifests.validateSettings(manifest, request)
        val row = settings.findById(id).orElseGet { PluginSettings(id) }
        row.enabled = request.enabled; row.allowedRolesCsv = request.allowedRoles.sorted().joinToString(",")
        row.searchEnabled = request.searchEnabled
        row.tableAccessJson = mapper.writeValueAsString(request.tableAccess)
        return view(manifest, settings.saveAndFlush(row))
    }
    private val mapper = jacksonObjectMapper()
    private fun view(manifest: PluginManifest, row: PluginSettings?): PluginView {
        val overrides: Map<String, PluginTableAccess> = row?.let { mapper.readValue(it.tableAccessJson, object : TypeReference<Map<String, PluginTableAccess>>() {}) } ?: emptyMap()
        return PluginView(manifest, row?.enabled == true,
            row?.allowedRolesCsv?.split(",")?.filter { it in manifest.supportedRoles }?.toSet() ?: manifest.supportedRoles,
            row?.searchEnabled == true && PluginExtension.SEARCH in manifest.extensions,
            manifest.tables.associate { it.name to (overrides[it.name] ?: PluginTableAccess(it.readRoles, it.writeRoles)) })
    }
}
