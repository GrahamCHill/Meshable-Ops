package com.starter.plugins

import com.starter.exception.BadRequestException
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.plugins.packages.PluginPackageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Service
class PluginLifecycle(private val registry: PluginRegistry, private val settings: PluginSettingsRepository, private val packages: PluginPackageRepository,
    private val jdbc: org.springframework.jdbc.core.JdbcTemplate) {
    @Transactional fun remove(id: String, request: PluginClearRequest) {
        if (request.confirmation != id) throw BadRequestException("Type the plugin ID to confirm removal")
        registry.definition(id)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('workspace-extension-installation'))", org.springframework.jdbc.core.RowCallbackHandler {})
        val row = settings.findById(id).orElseGet { PluginSettings(id) }
        row.enabled = false; row.searchEnabled = false; row.removed = true; settings.saveAndFlush(row)
        if (packages.existsById(id)) packages.deleteById(id)
        // Business tables and encrypted configuration remain. Clear-data is a separate explicit action.
    }
    @Transactional fun restore(id: String) {
        registry.definition(id)
        val row = settings.findById(id).orElseGet { PluginSettings(id) }
        row.removed = false; row.enabled = false; settings.saveAndFlush(row)
    }
}
@RestController @RequestMapping("/api/admin/plugins") @PreAuthorize("hasRole('SUPER_ADMIN')")
class PluginLifecycleController(private val lifecycle: PluginLifecycle) {
    @DeleteMapping("/{id}") fun remove(@PathVariable id: String, @RequestBody request: PluginClearRequest) = lifecycle.remove(id, request)
    @PostMapping("/{id}/restore") fun restore(@PathVariable id: String) = lifecycle.restore(id)
}
