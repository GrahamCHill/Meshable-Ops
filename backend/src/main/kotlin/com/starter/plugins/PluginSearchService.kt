package com.starter.plugins

import com.starter.model.postgres.User
import com.starter.service.VectorSearchPoint
import org.springframework.stereotype.Service

/** Providers must apply row ownership/ACLs in the database query, before LIMIT. */
interface PluginSearchProvider {
    val pluginId: String
    val tableName: String
    fun search(query: String, limit: Int, actor: User): List<VectorSearchPoint>
}

@Service
class PluginSearchService(private val providers: List<PluginSearchProvider>, private val registry: PluginRegistry,
    private val actors: PluginActor) {
    init {
        require(providers.map { it.pluginId to it.tableName }.distinct().size == providers.size)
        providers.forEach { provider ->
            val manifest = registry.definition(provider.pluginId)
            require(PluginExtension.SEARCH in manifest.extensions && manifest.tables.any { it.name == provider.tableName }) { "Search providers must declare their plugin table" }
        }
    }
    fun search(email: String, query: String, limit: Int): List<VectorSearchPoint> {
        val actor = actors.current(email)
        val allowed = registry.available(actor.role).filter { it.searchEnabled }.associateBy { it.manifest.id }
        val matches = providers.filter { provider -> actor.role in (allowed[provider.pluginId]?.tableAccess?.get(provider.tableName)?.readRoles ?: emptySet()) }
            .associateWith { it.search(query, limit, actor) }
        val currentActor = actors.current(email)
        // Providers may have used ADMIN to read other owners' rows. A role change
        // requires a fresh query; checking only the new table role is insufficient.
        if (currentActor.id != actor.id || currentActor.role != actor.role) return emptyList()
        val current = registry.available(currentActor.role).filter { it.searchEnabled }.associateBy { it.manifest.id }
        return matches.filterKeys { provider -> currentActor.role in (current[provider.pluginId]?.tableAccess?.get(provider.tableName)?.readRoles ?: emptySet()) }
            .values.flatten().sortedByDescending { it.score }.take(limit)
    }
}
