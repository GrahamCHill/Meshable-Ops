package com.starter.plugins

import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PluginActivationChange(val id: String, val enabled: Boolean, val expectedRevision: Long)
data class PluginActivationRequest(val changes: List<PluginActivationChange>)

/** A single transaction patches activation only; credentials, roles and table/search policies are retained. */
@Service
class PluginActivationService(private val settings: PluginSettingsRepository, private val registry: PluginRegistry) {
    @Transactional
    fun apply(request: PluginActivationRequest): List<PluginView> {
        val changes = request.changes
        if (changes.isEmpty() || changes.size > 100 || changes.map { it.id }.distinct().size != changes.size)
            throw BadRequestException("Choose 1–100 unique plugins")
        val locked = settings.lockForActivation(changes.map { it.id }.sorted()).associateBy { it.pluginId }
        val rows = changes.map { change ->
            val manifest = registry.definition(change.id)
            val existing = locked[change.id]
            if (existing?.removed == true) throw BadRequestException("Restore or reinstall removed plugins first")
            if ((existing?.revision ?: -1) != change.expectedRevision) throw ConflictException("Plugin settings changed. Reload before saving activation choices.")
            change to (existing ?: PluginSettings(change.id, allowedRolesCsv = manifest.defaultRoles.sorted().joinToString(",")))
        }
        rows.forEach { (change, row) -> row.enabled = change.enabled }
        settings.saveAllAndFlush(rows.map { it.second })
        return registry.installed()
    }
}
