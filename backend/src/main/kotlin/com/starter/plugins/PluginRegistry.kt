package com.starter.plugins

import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.starter.exception.ForbiddenException

@Service
class PluginRegistry(definitions: List<WorkspacePlugin>, private val settings: PluginSettingsRepository, private val access: com.starter.access.AccessControlService? = null,
    private val packages: com.starter.plugins.packages.InstalledPluginCatalogue? = null, private val versions: PluginVersionPolicy = PluginVersionPolicy(),
    private val jdbc: JdbcTemplate? = null) {
    private val modules = definitions.associateBy { it.manifest.id }
    private val bundled: Map<String, PluginManifest> = definitions.map { it.manifest }.also { all ->
        require(all.map { it.id }.distinct().size == all.size) { "Plugin IDs must be unique" }
        definitions.forEach {
            PluginManifests.validateIdentity(it.manifest)
            PluginManifests.validate(it.manifest, it.retainedTables)
        }
    }.map(PluginPresentation::defaults).associateBy { it.id }

    /** Plugin rows and uploaded descriptors as read for one registry call. */
    private open inner class Reads {
        // Retain uploaded descriptors across releases. A newly bundled ID collision is blocked, never silently substituted.
        open val manifests: Map<String, PluginManifest> by lazy { bundled + (packages?.manifests()?.associateBy { it.id } ?: emptyMap()) }
        open fun rows(): Map<String, PluginSettings> = settings.findAll().associateBy { it.pluginId }
        open fun row(id: String): PluginSettings? = settings.findById(id).orElse(null)
        open fun uploaded(id: String) = packages?.contains(id) == true
        open fun extensionsAllowed() = packages?.allowed() == true
        open fun author(id: String) = packages?.author(id)
    }

    /** Detached copy shared between requests while [stamp] still matches the database. */
    private inner class Snapshot(val stamp: String) : Reads() {
        private val saved = settings.findAll().associate { it.pluginId to PluginSettings(it.pluginId, it.enabled, it.allowedRolesCsv, it.searchEnabled, it.tableAccessJson, it.removed, it.revision) }
        private val installed = packages?.installed().orEmpty().associateBy { it.manifest.id }
        private val allowed = packages?.allowed() == true
        override val manifests = bundled + installed.mapValues { it.value.manifest }
        override fun rows() = saved
        override fun row(id: String) = saved[id]
        override fun uploaded(id: String) = id in installed
        override fun extensionsAllowed() = allowed
        override fun author(id: String) = installed[id]?.author
    }

    private val cacheLock = Any()
    private var cached: Snapshot? = null
    private var generation = 0L
    private val pendingChanges = Any()

    // Hashes every activation/policy row, package identity and the extension switch, so other replicas and direct SQL are seen.
    private fun stamp(jdbc: JdbcTemplate) = jdbc.queryForObject("""SELECT md5(
        COALESCE((SELECT string_agg(s::text, ',' ORDER BY s.plugin_id) FROM plugin_settings s), '') || '|' ||
        COALESCE((SELECT string_agg(p.id || ':' || p.sha256 || ':' || p.installed_at, ',' ORDER BY p.id) FROM workspace_plugin_packages p), '') || '|' ||
        COALESCE((SELECT e::text FROM workspace_extension_security e WHERE e.id = 1), ''))""", String::class.java)!!

    private fun reads(): Reads {
        // Without a database stamp, or after this transaction changed plugin state, read through as before.
        val jdbc = jdbc ?: return Reads()
        if (TransactionSynchronizationManager.hasResource(pendingChanges)) return Reads()
        val stamp = stamp(jdbc)
        val (current, started) = synchronized(cacheLock) { cached to generation }
        if (current?.stamp == stamp) return current
        val loaded = Snapshot(stamp)
        // Publish only rows that match the stamp read before loading and no newer local change.
        if (stamp(jdbc) == stamp) synchronized(cacheLock) { if (generation == started) cached = loaded }
        return loaded
    }

    /** Writers call this after changing plugin settings, packages or the extension switch. */
    fun invalidate() {
        synchronized(cacheLock) { generation++; cached = null }
        if (!TransactionSynchronizationManager.isSynchronizationActive() || TransactionSynchronizationManager.hasResource(pendingChanges)) return
        // Until completion this transaction reads its own uncommitted rows and never publishes them.
        TransactionSynchronizationManager.bindResource(pendingChanges, true)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                TransactionSynchronizationManager.unbindResourceIfPossible(pendingChanges)
                synchronized(cacheLock) { generation++; cached = null }
            }
        })
    }

    @Transactional(readOnly = true)
    fun installed(): List<PluginView> = reads().let { catalogue(it, it.rows(), "ADMIN") }
    fun available(role: String) = reads().let { catalogue(it, it.rows(), role) }.filter { it.active }

    private fun accessFeature(id: String) = modules[id]?.accessFeature ?: "plugin.$id"
    private fun canAccess(id: String) = modules[id]?.accessViews?.any { access?.allows(it.id) != false } ?: (access?.allows(accessFeature(id)) != false)
    private fun inactiveAccessFeatures(reads: Reads, active: Set<String>) = reads.manifests.keys.filter { it !in active }.flatMap { id ->
        listOf("plugin.$id") + modules[id]?.accessViews.orEmpty().map { it.id }
    }.toSet()
    fun effectiveAccess(value: com.starter.access.MyAccess): com.starter.access.MyAccess {
        val reads = reads()
        val active = catalogue(reads, reads.rows(), value.role).filter { it.active }.map { it.manifest.id }.toSet()
        val hidden = inactiveAccessFeatures(reads, active)
        return value.copy(features = value.features - hidden, views = value.views.filterNot { it.id in hidden })
    }
    fun effectiveAccessControls(value: com.starter.access.AccessPolicyView): com.starter.access.AccessPolicyView {
        // Show configurable views based on activation, independently of the permissions being edited.
        val reads = reads()
        val active = catalogue(reads, reads.rows(), "ADMIN", checkAccess = false).filter { it.active }.map { it.manifest.id }.toSet()
        val hidden = inactiveAccessFeatures(reads, active)
        return value.copy(features = value.features.filterNot { it.id in hidden })
    }

    fun definition(id: String) = reads().manifests[id] ?: throw NotFoundException("Plugin not installed")
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
        val reads = reads()
        if (!supporting && !canAccess(id)) throw NotFoundException("Plugin unavailable")
        checkEnabled(reads, id, role, !supporting)
        val policy = view(reads, reads.manifests[id] ?: throw NotFoundException("Plugin not installed"), reads.row(id)).tableAccess[table]
            ?: throw NotFoundException("Plugin table not declared")
        if (com.starter.access.Roles.plugin(role) !in (if (write) policy.writeRoles else policy.readRoles)) throw ForbiddenException("Your role cannot ${if (write) "change" else "read"} this plugin table")
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun requireAvailable(id: String, role: String) {
        if (!canAccess(id)) throw NotFoundException("Plugin unavailable")
        checkEnabled(reads(), id, role, true)
    }

    /** Capability composition may grant a run PDF without granting access to the owner's document library. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun requireEnabled(id: String, role: String) = checkEnabled(reads(), id, role, false)

    private fun checkEnabled(reads: Reads, id: String, role: String, checkAccess: Boolean) {
        val manifest = reads.manifests[id] ?: throw NotFoundException("Plugin unavailable")
        val view = view(reads, manifest, reads.row(id))
        if (!view.enabled || view.removed || view.blockedBySecurity || com.starter.access.Roles.plugin(role) !in view.allowedRoles) throw NotFoundException("Plugin unavailable")
        if (manifest.requiresCapabilities.isNotEmpty() || manifest.requires.isNotEmpty() || manifest.incompatibleWith.isNotEmpty() || manifest.apiVersion != 1 || id in bundled && reads.uploaded(id) || reads.manifests.values.any { id in it.incompatibleWith }) {
            val saved = reads.rows().toMutableMap()
            reads.row(id)?.let { saved[id] = it }
            if (catalogue(reads, saved, role, checkAccess).none { it.manifest.id == id && it.active }) throw NotFoundException("Plugin unavailable: check plugin API compatibility, conflicts, and required supporting plugins")
        }
    }

    @Transactional
    fun configure(id: String, request: PluginSettingsRequest): PluginView {
        // Writes read this transaction's rows, never the shared snapshot.
        val reads = Reads()
        val manifest = reads.manifests[id] ?: throw NotFoundException("Plugin not installed")
        if ("ADMIN" !in request.allowedRoles || request.allowedRoles.any { it !in setOf("ADMIN", "USER") }) throw BadRequestException("Choose administrators or all signed-in users")
        PluginManifests.validateSettings(manifest, request)
        val row = settings.findById(id).orElseGet { PluginSettings(id) }
        if (row.removed) throw BadRequestException("Restore or reinstall this plugin before changing it")
        row.enabled = request.enabled; row.allowedRolesCsv = request.allowedRoles.sorted().joinToString(",")
        row.searchEnabled = request.searchEnabled
        row.tableAccessJson = mapper.writeValueAsString(request.tableAccess)
        val saved = settings.saveAndFlush(row)
        invalidate()
        return catalogue(reads, settings.findAll().associateBy { it.pluginId } + (id to saved), "ADMIN").first { it.manifest.id == id }
    }
    private fun catalogue(reads: Reads, saved: Map<String, PluginSettings>, role: String, checkAccess: Boolean = true): List<PluginView> {
        val manifests = reads.manifests
        val base = manifests.values.sortedBy { it.name }.map { view(reads, it, saved[it.id]) }
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
    private fun view(reads: Reads, manifest: PluginManifest, row: PluginSettings?): PluginView {
        val overrides: Map<String, PluginTableAccess> = row?.let { mapper.readValue(it.tableAccessJson, object : TypeReference<Map<String, PluginTableAccess>>() {}) } ?: emptyMap()
        val uploaded = manifest.id !in bundled || reads.uploaded(manifest.id)
        val version = versions.assess(manifest)
        return PluginView(manifest, row?.enabled == true,
            row?.allowedRolesCsv?.split(",")?.filter { it in manifest.supportedRoles }?.toSet() ?: manifest.defaultRoles,
            row?.searchEnabled == true && PluginExtension.SEARCH in manifest.extensions,
            manifest.tables.associate { it.name to (overrides[it.name] ?: PluginTableAccess(it.readRoles, it.writeRoles)) },
            source = if (uploaded) "ZIP" else "BUNDLED", author = if (uploaded) reads.author(manifest.id) else null,
            blockedBySecurity = uploaded && !reads.extensionsAllowed(), removed = row?.removed == true,
            applicationVersion=versions.applicationVersion, versionCompatibility=version.status, compatibilityWarnings=version.warnings, settingsRevision=row?.revision ?: -1)
    }
}
