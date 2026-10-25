package com.starter.plugins.knowledge

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.Roles
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import com.starter.plugins.PluginRegistry
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnowledgeAccess(private val actors: PluginActor, private val plugins: PluginRegistry,
    private val settings: KnowledgeSettingsRepository, private val mapper: ObjectMapper) {
    fun user(email: String, write: Boolean = false): User {
        val actor = actors.fresh(email)
        plugins.requireTable(KnowledgePlugin.ID, KnowledgePlugin.PAGES, actor.role, write)
        return actor
    }
    fun admin(email: String): User {
        val actor = user(email)
        if (!Roles.admin(actor.role)) throw ForbiddenException("Only administrators can change documentation export permissions and covers")
        plugins.requireTable(KnowledgePlugin.ID, KnowledgePlugin.SETTINGS, actor.role, true)
        return actor
    }
    fun exporters(row: KnowledgeSettings?): Set<UUID> = row?.let { mapper.readValue(it.exporterIdsJson, object : TypeReference<Set<UUID>>() {}) } ?: emptySet()
    fun requireExport(email: String): KnowledgeSettings {
        val actor = user(email)
        val row = settings.findById(1).orElse(null)
        if (actor.id !in exporters(row)) throw ForbiddenException("An administrator must grant you documentation export permission")
        return row!!
    }
    fun status(email: String): KnowledgeStatus {
        val actor = user(email)
        val canEdit = try { plugins.requireTable(KnowledgePlugin.ID, KnowledgePlugin.PAGES, actor.role, true); true } catch (_: ForbiddenException) { false }
        return KnowledgeStatus(canEdit, actor.id in exporters(settings.findById(1).orElse(null)), Roles.admin(actor.role))
    }
}
