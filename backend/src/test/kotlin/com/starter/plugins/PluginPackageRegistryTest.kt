package com.starter.plugins

import com.starter.exception.NotFoundException
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.kanban.KanbanPlugin
import com.starter.plugins.packages.InstalledPluginCatalogue
import com.starter.plugins.packages.PluginAuthor
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class PluginPackageRegistryTest {
    private val records = mock(PluginSettingsRepository::class.java)
    private val packages = mock(InstalledPluginCatalogue::class.java)
    private val manifest = PluginManifest("zip-notes", "Notes", "1.0.0", "Notes", extensions = setOf(PluginExtension.PAGE))
    private val row = PluginSettings("zip-notes", true)
    private fun registry(): PluginRegistry {
        `when`(packages.manifests()).thenReturn(listOf(manifest))
        `when`(packages.author("zip-notes")).thenReturn(PluginAuthor("", "", "", ""))
        `when`(records.findAll()).thenReturn(listOf(row, PluginSettings("kanban", true)))
        `when`(records.findById("zip-notes")).thenReturn(Optional.of(row))
        `when`(records.findById("kanban")).thenReturn(Optional.of(PluginSettings("kanban", true)))
        return PluginRegistry(listOf(KanbanPlugin()), records, packages = packages)
    }
    @Test fun `global security switch removes ZIP plugins immediately but preserves enabled state and bundled plugins`() {
        val registry = registry()
        `when`(packages.allowed()).thenReturn(true)
        assertThat(registry.available("USER").map { it.manifest.id }).contains("zip-notes", "kanban")
        registry.requireAvailable("zip-notes", "USER")
        `when`(packages.allowed()).thenReturn(false)
        assertThat(registry.available("USER").map { it.manifest.id }).containsExactly("kanban")
        assertThat(registry.installed().first { it.manifest.id == "zip-notes" }.enabled).isTrue()
        assertThat(registry.installed().first { it.manifest.id == "zip-notes" }.blockedBySecurity).isTrue()
        assertThatThrownBy { registry.requireAvailable("zip-notes", "USER") }.isInstanceOf(NotFoundException::class.java)
        `when`(packages.allowed()).thenReturn(true)
        registry.requireAvailable("zip-notes", "USER")
    }
    @Test fun `installed packages become definitions without rebuilding and still respect allowed roles`() {
        val registry = registry()
        `when`(packages.allowed()).thenReturn(true)
        row.allowedRolesCsv = "ADMIN"
        assertThat(registry.definition("zip-notes")).isEqualTo(manifest)
        assertThat(registry.available("USER").map { it.manifest.id }).doesNotContain("zip-notes")
        assertThat(registry.available("SUPER_ADMIN").map { it.source }).contains("ZIP")
        `when`(packages.manifests()).thenReturn(emptyList())
        assertThatThrownBy { registry.definition("zip-notes") }.isInstanceOf(NotFoundException::class.java)
    }
}
