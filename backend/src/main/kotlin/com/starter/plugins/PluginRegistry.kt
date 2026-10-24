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
    private val packages: com.starter.plugins.packages.InstalledPluginCatalogue? = null, private val versions: PluginVersionPolicy = PluginVersionPolicy()) {
    private val modules = definitions.associateBy { it.manifest.id }
    // Retain uploaded descriptors across releases. A newly bundled ID collision is blocked, never silently substituted.
    private val manifests: Map<String, PluginManifest> get() = bundled + (packages?.manifests()?.associateBy { it.id } ?: emptyMap())
    private val bundled: Map<String, PluginManifest>
    init {
        val all = definitions.map { it.manifest }
        require(all.map { it.id }.distinct().size == all.size) { "Plugin IDs must be unique" }
        definitions.forEach {
            PluginManifests.validateIdentity(it.manifest)
            PluginManifests.validate(it.manifest, it.retainedTables)
        }
        bundled = all.map(PluginPresentation::defaults).associateBy { it.id }
    }
    @Transactional(readOnly = true)
    fun installed(): List<PluginView> {
        val saved = settings.findAll().associateBy { it.pluginId }
        return catalogue(saved, "ADMIN")
    }
    fun available(role: String) = catalogue(settings.findAll().associateBy { it.pluginId }, role).filter { it.active }

    private fun accessFeature(id: String) = modules[id]?.accessFeature ?: "plugin.$id"
    private fun canAccess(id: String) = modules[id]?.accessViews?.any { access?.allows(it.id) != false } ?: (access?.allows(accessFeature(id)) != false)
    fun effectiveAccess(value: com.starter.access.MyAccess): com.starter.access.MyAccess {
        val active = available(value.role).map { it.manifest.id }.toSet()
        val hidden = modules.values.filter { it.manifest.id !in active }.flatMap { it.accessViews }.map { it.id }.toSet()
        return value.copy(features = value.features - hidden)
    }

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

    fun requireTable(id: String, table: String, role: String, write: Boolean = false, supporting: Boolean = false) {
        if (supporting) requireEnabled(id, role) else requireAvailable(id, role)
        val policy = view(definition(id), settings.findById(id).orElse(null)).tableAccess[table]
            ?: throw NotFoundException("Plugin table not declared")
        if (com.starter.access.Roles.plugin(role) !in (if (write) policy.writeRoles else policy.readRoles)) throw ForbiddenException("Your role cannot ${if (write) "change" else "read"} this plugin table")
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun requireAvailable(id: String, role: String) {
        if (!canAccess(id)) throw NotFoundException("Plugin unavailable")
        checkEnabled(id, role, true)
    }

    /** Capability composition may grant a run PDF without granting access to the owner's document library. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun requireEnabled(id: String, role: String) = checkEnabled(id, role, false)

    private fun checkEnabled(id: String, role: String, checkAccess: Boolean) {
        val manifest = manifests[id] ?: throw NotFoundException("Plugin unavailable")
        val view = view(manifest, settings.findById(id).orElse(null))
        if (!view.enabled || view.removed || view.blockedBySecurity || com.starter.access.Roles.plugin(role) !in view.allowedRoles) throw NotFoundException("Plugin unavailable")
        if (manifest.requiresCapabilities.isNotEmpty() || manifest.requires.isNotEmpty() || manifest.incompatibleWith.isNotEmpty() || manifest.apiVersion != 1 || id in bundled && packages?.contains(id) == true || manifests.values.any { id in it.incompatibleWith }) {
            val saved = settings.findAll().associateBy { it.pluginId }.toMutableMap()
            settings.findById(id).orElse(null)?.let { saved[id] = it }
            if (catalogue(saved, role, checkAccess).none { it.manifest.id == id && it.active }) throw NotFoundException("Plugin unavailable: check plugin API compatibility, conflicts, and required supporting plugins")
        }
    }

    @Transactional
    fun configure(id: String, request: PluginSettingsRequest): PluginView {
        val manifest = manifests[id] ?: throw NotFoundException("Plugin not installed")
        if ("ADMIN" !in request.allowedRoles || request.allowedRoles.any { it !in setOf("ADMIN", "USER") }) throw BadRequestException("Choose administrators or all signed-in users")
        PluginManifests.validateSettings(manifest, request)
        val row = settings.findById(id).orElseGet { PluginSettings(id) }
        if (row.removed) throw BadRequestException("Restore or reinstall this plugin before changing it")
        row.enabled = request.enabled; row.allowedRolesCsv = request.allowedRoles.sorted().joinToString(",")
        row.searchEnabled = request.searchEnabled
        row.tableAccessJson = mapper.writeValueAsString(request.tableAccess)
        val saved = settings.saveAndFlush(row)
        return catalogue(settings.findAll().associateBy { it.pluginId } + (id to saved), "ADMIN").first { it.manifest.id == id }
    }
    private fun catalogue(saved: Map<String, PluginSettings>, role: String, checkAccess: Boolean = true): List<PluginView> {
        val base = manifests.values.sortedBy { it.name }.map { view(it, saved[it.id]) }
        val candidates = base.filter { it.enabled && !it.removed && !it.blockedBySecurity && com.starter.access.Roles.plugin(role) in it.allowedRoles && (!checkAccess || canAccess(it.manifest.id)) }
        val issues = base.associate { plugin -> plugin.manifest.id to buildSet {
            if (plugin.source == "ZIP" && plugin.manifest.id in bundled) add("An installed ZIP plugin has the same ID as a bundled module in this release. Both are blocked; the uploaded package and its data are retained. Resolve the ID conflict before enabling it.")
            if (plugin.manifest.apiVersion != 1) add("Requires host plugin API ${plugin.manifest.apiVersion}; this host supports 1")
            candidates.filter { other -> other.manifest.id != plugin.manifest.id }.forEach { other ->
                plugin.manifest.incompatibleWith[other.manifest.id]?.let { add("${other.manifest.name}: $it") }
                other.manifest.incompatibleWith[plugin.manifest.id]?.let { add("${other.manifest.name}: $it") }
            }
        } }
        val active = mutableSetOf<String>()
        repeat(base.size) {
            val supplied = base.filter { it.manifest.id in active }.flatMap { it.manifest.capabilities.keys }.toSet()
            candidates.filter { issues[it.manifest.id].isNullOrEmpty() && active.containsAll(it.manifest.requires) && supplied.containsAll(it.manifest.requiresCapabilities) }.forEach { active += it.manifest.id }
        }
        val providers = base.filter { it.manifest.id in active }
        val supplied = providers.flatMap { it.manifest.capabilities.keys }.toSet()
        val labels = manifests.values.flatMap { it.capabilities.entries }.associate { it.key to it.value }
        return base.map { plugin -> plugin.copy(active = plugin.manifest.id in active, compatibilityIssues = issues[plugin.manifest.id].orEmpty(),
            missingDependencies = (plugin.manifest.requiresCapabilities - supplied).map { labels[it] ?: it }.toSet() + (plugin.manifest.requires - active).map { manifests[it]?.name ?: it },
            features = plugin.manifest.features.map { feature -> PluginFeatureView(feature.id, feature.label,
                plugin.manifest.id in active && supplied.containsAll(feature.requires),
                providers.filter { it.manifest.capabilities.keys.intersect(feature.requires).isNotEmpty() }.map { it.manifest.name }.toSet(),
                (feature.requires - supplied).map { labels[it] ?: it }.toSet()) }) }
    }
    private val mapper = jacksonObjectMapper()
    private fun view(manifest: PluginManifest, row: PluginSettings?): PluginView {
        val overrides: Map<String, PluginTableAccess> = row?.let { mapper.readValue(it.tableAccessJson, object : TypeReference<Map<String, PluginTableAccess>>() {}) } ?: emptyMap()
        val uploaded = manifest.id !in bundled || packages?.contains(manifest.id) == true
        val version = versions.assess(manifest)
        return PluginView(manifest, row?.enabled == true,
            row?.allowedRolesCsv?.split(",")?.filter { it in manifest.supportedRoles }?.toSet() ?: manifest.defaultRoles,
            row?.searchEnabled == true && PluginExtension.SEARCH in manifest.extensions,
            manifest.tables.associate { it.name to (overrides[it.name] ?: PluginTableAccess(it.readRoles, it.writeRoles)) },
            source = if (uploaded) "ZIP" else "BUNDLED", author = if (uploaded) packages?.author(manifest.id) else null,
            blockedBySecurity = uploaded && packages?.allowed() != true, removed = row?.removed == true,
            applicationVersion=versions.applicationVersion, versionCompatibility=version.status, compatibilityWarnings=version.warnings, settingsRevision=row?.revision ?: -1)
    }
}
