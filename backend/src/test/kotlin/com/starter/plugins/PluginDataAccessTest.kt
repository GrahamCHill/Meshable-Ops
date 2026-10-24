package com.starter.plugins

import com.starter.exception.*
import com.starter.model.postgres.*
import com.starter.plugins.kanban.*
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.service.VectorSearchPoint
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class PluginDataAccessTest {
    private val settings = mock(PluginSettingsRepository::class.java)
    private val registry = PluginRegistry(listOf(KanbanPlugin()), settings)
    private val row = PluginSettings("kanban", true)
    init {
        `when`(settings.findById("kanban")).thenReturn(Optional.of(row))
        `when`(settings.findAll()).thenReturn(listOf(row))
        `when`(settings.saveAndFlush(row)).thenReturn(row)
    }
    @Test fun `table policy grants read without write and cannot invent table names`() {
        registry.configure("kanban", PluginSettingsRequest(true, tableAccess = mapOf("plugin_kanban_boards" to PluginTableAccess(setOf("ADMIN", "USER"), setOf("ADMIN")))))
        registry.requireTable("kanban", "plugin_kanban_boards", "USER")
        assertThatThrownBy { registry.requireTable("kanban", "plugin_kanban_boards", "USER", true) }.isInstanceOf(ForbiddenException::class.java)
        registry.requireTable("kanban", "plugin_kanban_boards", "ADMIN", true)
        assertThatThrownBy { registry.configure("kanban", PluginSettingsRequest(true, tableAccess = mapOf("users" to PluginTableAccess(setOf("ADMIN"), setOf("ADMIN"))))) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { registry.configure("kanban", PluginSettingsRequest(true, tableAccess = mapOf("plugin_kanban_boards" to PluginTableAccess(setOf("ADMIN"), setOf("ADMIN", "USER"))))) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `untrusted identifiers and submenu paths fail at startup`() {
        val manifest = KanbanPlugin().manifest
        listOf(manifest.copy(tables = listOf(PluginTable("users", "Users"))),
            manifest.copy(tables = listOf(PluginTable("plugin_kanban_x;delete", "Bad"))),
            manifest.copy(menuItems = listOf(PluginMenuItem("../admin", "Bad"))),
            manifest.copy(menuItems = listOf(PluginMenuItem("https://bad", "Bad")))).forEach { invalid ->
            assertThatThrownBy { PluginRegistry(listOf(object : WorkspacePlugin {override val manifest = invalid}), settings) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
    @Test fun `clear requires exact confirmation and deletes only compiled declared tables`() {
        val jdbc = mock(JdbcTemplate::class.java)
        val data = PluginDataService(registry, jdbc)
        assertThatThrownBy { data.clear("kanban", PluginClearRequest("users")) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { data.clear("users", PluginClearRequest("users")) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(jdbc)
        `when`(jdbc.update("DELETE FROM \"plugin_kanban_boards\"")).thenReturn(3)
        assertThat(data.clear("kanban", PluginClearRequest("kanban")).deletedRows).containsEntry("plugin_kanban_boards", 3)
        val ordered = inOrder(jdbc)
        ordered.verify(jdbc).execute("LOCK TABLE \"plugin_kanban_comments\" IN SHARE ROW EXCLUSIVE MODE")
        ordered.verify(jdbc).execute("LOCK TABLE \"plugin_kanban_development_links\" IN SHARE ROW EXCLUSIVE MODE")
        ordered.verify(jdbc).execute("LOCK TABLE \"plugin_kanban_boards\" IN SHARE ROW EXCLUSIVE MODE")
        ordered.verify(jdbc).execute("LOCK TABLE \"plugin_kanban_estimation\" IN SHARE ROW EXCLUSIVE MODE")
        ordered.verify(jdbc).update("DELETE FROM \"plugin_kanban_comments\"")
        ordered.verify(jdbc).update("DELETE FROM \"plugin_kanban_development_links\"")
        ordered.verify(jdbc).update("DELETE FROM \"plugin_kanban_boards\"")
        ordered.verify(jdbc).update("DELETE FROM \"plugin_kanban_estimation\"")
        verifyNoMoreInteractions(jdbc)
    }
    @Test fun `search excludes disabled excluded and table restricted providers before any query`() {
        val actors = mock(PluginActor::class.java)
        val actor = User(email = "member@example.test")
        `when`(actors.current(actor.email)).thenReturn(actor)
        var calls = 0
        val provider = object : PluginSearchProvider {
            override val pluginId = "kanban"; override val tableName = "plugin_kanban_boards"
            override fun search(query: String, limit: Int, actor: User): List<VectorSearchPoint> {calls++; return listOf(VectorSearchPoint("plugin:kanban:1", 1f, emptyMap()))}
        }
        val search = PluginSearchService(listOf(provider), registry, actors)
        assertThat(search.search(actor.email, "task", 5)).isEmpty()
        assertThat(calls).isZero()
        row.searchEnabled = true
        assertThat(search.search(actor.email, "task", 5)).hasSize(1)
        row.enabled = false
        assertThat(search.search(actor.email, "task", 5)).isEmpty()
        row.enabled = true
        registry.configure("kanban", PluginSettingsRequest(true, searchEnabled = true, tableAccess = mapOf("plugin_kanban_boards" to PluginTableAccess(setOf("ADMIN"), setOf("ADMIN")))))
        assertThat(search.search(actor.email, "task", 5)).isEmpty()
        assertThat(calls).isEqualTo(1)
    }
    @Test fun `revocation during a provider query removes its results`() {
        val actors = mock(PluginActor::class.java)
        val actor = User(email = "member@example.test")
        `when`(actors.current(actor.email)).thenReturn(actor)
        row.searchEnabled = true
        val provider = object : PluginSearchProvider {
            override val pluginId = "kanban"; override val tableName = "plugin_kanban_boards"
            override fun search(query: String, limit: Int, actor: User): List<VectorSearchPoint> {row.searchEnabled = false; return listOf(VectorSearchPoint("secret", 1f, emptyMap()))}
        }
        assertThat(PluginSearchService(listOf(provider), registry, actors).search(actor.email, "task", 5)).isEmpty()
    }
    @Test fun `admin demotion discards results queried with the former ownership bypass`() {
        val actors = mock(PluginActor::class.java)
        val actor = User(email = "admin@example.test", role = "ADMIN")
        `when`(actors.current(actor.email)).thenReturn(actor, actor.copy(role = "USER"))
        row.searchEnabled = true
        val provider = object : PluginSearchProvider {
            override val pluginId = "kanban"; override val tableName = "plugin_kanban_boards"
            override fun search(query: String, limit: Int, actor: User) = listOf(VectorSearchPoint("other-owner", 1f, emptyMap()))
        }
        assertThat(PluginSearchService(listOf(provider), registry, actors).search(actor.email, "task", 5)).isEmpty()
    }
}
