package com.starter.plugins

import com.starter.exception.BadRequestException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PluginDataService(private val registry: PluginRegistry, private val jdbc: JdbcTemplate, private val guards: List<PluginDataClearGuard> = emptyList()) {
    @Transactional
    fun clear(id: String, request: PluginClearRequest): PluginClearResult {
        val tables = registry.definition(id).tables
        if (request.confirmation != id || tables.isEmpty()) throw BadRequestException("Type the plugin ID to clear its data")
        guards.filter { it.pluginId == id }.forEach { it.check() }
        // Names come exclusively from validated plugin manifests. Never accept
        // table names or CASCADE from HTTP. FK failures roll back the whole clear.
        tables.forEach { jdbc.execute("LOCK TABLE \"${it.name}\" IN SHARE ROW EXCLUSIVE MODE") }
        return PluginClearResult(tables.associate { it.name to jdbc.update("DELETE FROM \"${it.name}\"") })
    }
}

/** Reviewed modules protect references owned by another plugin before an explicit destructive clear. */
interface PluginDataClearGuard { val pluginId: String; fun check() }
