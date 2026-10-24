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
class PluginRegistry(definitions: List<WorkspacePlugin>, private val settings: PluginSettingsRepository, private val access: com.starter.access.AccessControlService? = null,
    private val packages: com.starter.plugins.packages.InstalledPluginCatalogue? = null) {
    private val modules = definitions.associateBy { it.manifest.id }
    private val manifests: Map<String, PluginManifest> get() = (packages?.manifests()?.associateBy { it.id } ?: emptyMap()) + bundled
    private val bundled: Map<String, PluginManifest>
    init {
        val all = definitions.map { it.manifest }
        require(all.map { it.id }.distinct().size == all.size) { "Plugin IDs must be unique" }
        all.forEach {
            PluginManifests.validateIdentity(it)
            PluginManifests.validate(it)
        }
        bundled = all.associateBy { it.id }
    }
    @Transactional(readOnly = true)
    fun installed(): List<PluginView> {
        val saved = settings.findAll().associateBy { it.pluginId }
        return catalogue(saved, "ADMIN")
    }
    fun available(role: String) = catalogue(settings.findAll().associateBy { it.pluginId }, role).filter { it.active && (access?.allows("plugin.${it.manifest.id}") != false) }

    fun definition(id: String) = manifests[id] ?: throw NotFoundException("Plugin not installed")
    fun validateConfiguration(id: String, values: Map<String, String>) { definition(id); modules[id]?.validateConfiguration(values) }
    fun missingConfiguration(id: String, values: Map<String, String>) = modules[id]?.missingConfiguration(values)
        ?: definition(id).settingsFields.filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }

    fun requireFeature(id: String, feature: String, role: String) {
        val plugin = available(role).find { it.manifest.id == id }
        if (plugin?.features?.none { it.id == feature && it.available } != false) throw NotFoundException("Supporting plugins are unavailable")
    }
    fun requireCapability(caller: String, capability: String, provider: String, role: String) {
        requireAvailable(caller, role)
        if (available(role).none { it.manifest.id == provider && capability in it.manifest.capabilities }) throw NotFoundException("Supporting plugin unavailable")
    }

    fun requireTable(id: String, table: String, role: String, write: Boolean = false) {
        requireAvailable(id, role)
        val policy = view(definition(id), settings.findById(id).orElse(null)).tableAccess[table]
            ?: throw NotFoundException("Plugin table not declared")
        if (com.starter.access.Roles.plugin(role) !in (if (write) policy.writeRoles else policy.readRoles)) throw ForbiddenException("Your role cannot ${if (write) "change" else "read"} this plugin table")
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun requireAvailable(id: String, role: String) {
        if (access?.allows("plugin.$id") == false) throw NotFoundException("Plugin unavailable")
        val manifest = manifests[id] ?: throw NotFoundException("Plugin unavailable")
        val view = view(manifest, settings.findById(id).orElse(null))
        if (!view.enabled || view.blockedBySecurity || com.starter.access.Roles.plugin(role) !in view.allowedRoles) throw NotFoundException("Plugin unavailable")
        if (manifest.requiresCapabilities.isNotEmpty()) {
            val saved = settings.findAll().associateBy { it.pluginId }.toMutableMap()
            settings.findById(id).orElse(null)?.let { saved[id] = it }
            if (catalogue(saved, role).none { it.manifest.id == id && it.active }) throw NotFoundException("Enable the required supporting plugins first")
        }
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
        val saved = settings.saveAndFlush(row)
        return catalogue(settings.findAll().associateBy { it.pluginId } + (id to saved), "ADMIN").first { it.manifest.id == id }
    }
    private fun catalogue(saved: Map<String, PluginSettings>, role: String): List<PluginView> {
        val base = manifests.values.sortedBy { it.name }.map { view(it, saved[it.id]) }
        val active = mutableSetOf<String>()
        repeat(base.size) {
            val supplied = base.filter { it.manifest.id in active }.flatMap { it.manifest.capabilities.keys }.toSet()
            base.filter { it.enabled && !it.blockedBySecurity && com.starter.access.Roles.plugin(role) in it.allowedRoles && supplied.containsAll(it.manifest.requiresCapabilities) }.forEach { active += it.manifest.id }
        }
        val providers = base.filter { it.manifest.id in active }
        val supplied = providers.flatMap { it.manifest.capabilities.keys }.toSet()
        val labels = manifests.values.flatMap { it.capabilities.entries }.associate { it.key to it.value }
        return base.map { plugin -> plugin.copy(active = plugin.manifest.id in active,
            missingDependencies = (plugin.manifest.requiresCapabilities - supplied).map { labels[it] ?: it }.toSet(),
            features = plugin.manifest.features.map { feature -> PluginFeatureView(feature.id, feature.label,
                plugin.manifest.id in active && supplied.containsAll(feature.requires),
                providers.filter { it.manifest.capabilities.keys.intersect(feature.requires).isNotEmpty() }.map { it.manifest.name }.toSet(),
                (feature.requires - supplied).map { labels[it] ?: it }.toSet()) }) }
    }
    private val mapper = jacksonObjectMapper()
    private fun view(manifest: PluginManifest, row: PluginSettings?): PluginView {
        val overrides: Map<String, PluginTableAccess> = row?.let { mapper.readValue(it.tableAccessJson, object : TypeReference<Map<String, PluginTableAccess>>() {}) } ?: emptyMap()
        val uploaded = manifest.id !in bundled
        return PluginView(manifest, row?.enabled == true,
            row?.allowedRolesCsv?.split(",")?.filter { it in manifest.supportedRoles }?.toSet() ?: manifest.defaultRoles,
            row?.searchEnabled == true && PluginExtension.SEARCH in manifest.extensions,
            manifest.tables.associate { it.name to (overrides[it.name] ?: PluginTableAccess(it.readRoles, it.writeRoles)) },
            source = if (uploaded) "ZIP" else "BUNDLED", author = if (uploaded) packages?.author(manifest.id) else null,
            blockedBySecurity = uploaded && packages?.allowed() != true)
    }
}
