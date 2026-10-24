package com.starter.plugins

import com.starter.exception.*
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.kanban.KanbanPlugin
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class PluginRegistryTest {
    private val settings = mock(PluginSettingsRepository::class.java)
    private val registry = PluginRegistry(listOf(KanbanPlugin()), settings)

    @Test fun `installed plugins start disabled without writing seed rows`() {
        `when`(settings.findAll()).thenReturn(emptyList())
        `when`(settings.findById("kanban")).thenReturn(Optional.empty())
        assertThat(registry.installed().single().enabled).isFalse()
        assertThat(registry.available("ADMIN")).isEmpty()
        assertThatThrownBy { registry.requireAvailable("kanban", "ADMIN") }.isInstanceOf(NotFoundException::class.java)
        verify(settings, never()).saveAndFlush(any(PluginSettings::class.java))
    }
    @Test fun `roles and enabled state control the catalogue and API guard`() {
        val row = PluginSettings("kanban", true, "ADMIN")
        `when`(settings.findAll()).thenReturn(listOf(row))
        `when`(settings.findById("kanban")).thenReturn(Optional.of(row))
        assertThat(registry.available("USER")).isEmpty()
        assertThat(registry.available("ADMIN")).hasSize(1)
        registry.requireAvailable("kanban", "ADMIN")
        assertThatThrownBy { registry.requireAvailable("kanban", "USER") }.isInstanceOf(NotFoundException::class.java)
        row.enabled = false
        assertThatThrownBy { registry.requireAvailable("kanban", "ADMIN") }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `settings preserve explicit roles and reject administrator lockout or invented roles`() {
        val row = PluginSettings("kanban")
        `when`(settings.findById("kanban")).thenReturn(Optional.of(row))
        `when`(settings.saveAndFlush(row)).thenReturn(row)
        assertThat(registry.configure("kanban", PluginSettingsRequest(true, setOf("ADMIN"))).allowedRoles).containsExactly("ADMIN")
        listOf(emptySet(), setOf("USER"), setOf("ADMIN", "OTHER")).forEach { roles ->
            assertThatThrownBy { registry.configure("kanban", PluginSettingsRequest(true, roles)) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThatThrownBy { registry.configure("missing", PluginSettingsRequest(true)) }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `invalid duplicate and incompatible manifests fail at startup`() {
        assertThatThrownBy { PluginRegistry(listOf(KanbanPlugin(), KanbanPlugin()), settings) }.isInstanceOf(IllegalArgumentException::class.java)
        listOf(KanbanPlugin().manifest.copy(id = "../bad"), KanbanPlugin().manifest.copy(version = "latest")).forEach { manifest ->
            assertThatThrownBy { PluginRegistry(listOf(object : WorkspacePlugin { override val manifest = manifest }), settings) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
