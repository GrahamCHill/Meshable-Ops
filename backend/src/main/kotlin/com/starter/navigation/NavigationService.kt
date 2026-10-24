package com.starter.navigation

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.model.postgres.NavigationSettings
import com.starter.model.postgres.User
import com.starter.repository.postgres.NavigationSettingsRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NavigationService(private val repository: NavigationSettingsRepository, private val catalogue: NavigationCatalogue,
    private val json: ObjectMapper, private val jdbc: JdbcTemplate) {
    fun current(): NavigationPreferences {
        val row = repository.findById(1).orElse(null) ?: return NavigationPreferences()
        return NavigationPreferences(json.readValue(row.orderJson, object : TypeReference<List<String>>() {}),
            json.readValue(row.submenusJson, object : TypeReference<Map<String, List<String>>>() {}), row.revision)
    }
    fun admin() = NavigationView(current(), catalogue.all())
    fun member(actor: User): NavigationView {
        val items = catalogue.visible(actor)
        val preferences = current()
        val ids = items.map { it.id }.toSet()
        val sections = items.associate { it.id to it.children.map { child -> child.id }.toSet() }
        return NavigationView(preferences.copy(order = preferences.order.filter { it in ids }, submenus = preferences.submenus.filterKeys { it in ids }
            .mapValues { (id, order) -> order.filter { it in sections.getValue(id) } }), items)
    }
    @Transactional
    fun save(request: NavigationPreferences): NavigationView {
        if (request.order.size > 500 || request.submenus.size > 100 || request.submenus.values.any { it.size > 40 } ||
            json.writeValueAsBytes(request).size > 65536) throw BadRequestException("Navigation settings exceed the supported size")
        // Also serialize first saves, when no optimistic-lock row exists yet.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", RowCallbackHandler {}, "workspace-navigation")
        val previous = current()
        if (previous.revision != request.revision) throw ConflictException("Menu order changed. Reload the saved order before editing.")
        val items = catalogue.all()
        val known = items.map { it.id }.toSet() + previous.order
        if (request.order.distinct().size != request.order.size || request.order.any { it !in known }) invalid()
        val children = items.associate { it.id to it.children.map { child -> child.id }.toSet() }
        request.submenus.forEach { (id, order) ->
            val allowed = children[id].orEmpty() + previous.submenus[id].orEmpty()
            if (id !in children && id !in previous.submenus || order.distinct().size != order.size || order.any { it !in allowed }) invalid()
        }
        val row = repository.findById(1).orElseGet { NavigationSettings() }
        row.orderJson = json.writeValueAsString(request.order); row.submenusJson = json.writeValueAsString(request.submenus)
        val saved = repository.saveAndFlush(row)
        return NavigationView(request.copy(revision = saved.revision), items)
    }
    private fun invalid(): Nothing = throw BadRequestException("Use each declared menu or section at most once. Menu settings cannot add new destinations.")
}
