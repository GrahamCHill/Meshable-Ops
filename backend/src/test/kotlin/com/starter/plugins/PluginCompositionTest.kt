package com.starter.plugins

import com.starter.model.postgres.PluginSettings
import com.starter.plugins.calendar.*
import com.starter.plugins.kanban.KanbanPlugin
import com.starter.plugins.sourcecontrol.GitHubPlugin
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.exception.NotFoundException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class PluginCompositionTest {
    private val settings = mock(PluginSettingsRepository::class.java)
    private val rows = mutableListOf<PluginSettings>()
    private val registry = PluginRegistry(listOf(CalendarPlugin(),MicrosoftCalendarPlugin(),KanbanPlugin(),GitHubPlugin()),settings)
    init {
        `when`(settings.findAll()).thenAnswer { rows }
        `when`(settings.findById(anyString())).thenAnswer { call -> Optional.ofNullable(rows.find { it.pluginId == call.arguments[0] }) }
    }
    @Test fun `hard dependencies block enabled providers until the base plugin is available`() {
        rows += PluginSettings("calendar-microsoft",true,"ADMIN")
        assertThat(registry.installed().first { it.manifest.id == "calendar-microsoft" }.missingDependencies).containsExactly("Calendar events")
        assertThat(registry.available("ADMIN")).isEmpty()
        assertThatThrownBy { registry.requireAvailable("calendar-microsoft","ADMIN") }.isInstanceOf(NotFoundException::class.java)
        rows += PluginSettings("calendar",true,"ADMIN")
        registry.requireCapability("calendar","calendar.provider","calendar-microsoft","ADMIN")
        rows.last().enabled = false
        assertThatThrownBy { registry.requireCapability("calendar","calendar.provider","calendar-microsoft","ADMIN") }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `optional features follow provider roles and disabling without blocking the base plugin`() {
        rows += PluginSettings("kanban",true,"ADMIN,USER")
        rows += PluginSettings("github",true,"ADMIN")
        registry.requireFeature("kanban","development-links","ADMIN")
        assertThatThrownBy { registry.requireFeature("kanban","development-links","USER") }.isInstanceOf(NotFoundException::class.java)
        rows.last().enabled = false
        registry.requireAvailable("kanban","USER")
        assertThatThrownBy { registry.requireFeature("kanban","development-links","ADMIN") }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `dependency cycles never activate themselves`() {
        fun plugin(id: String,provides: String,requires: String) = object : WorkspacePlugin {
            override val manifest = PluginManifest(id,id,"1.0.0","",capabilities=mapOf(provides to provides),requiresCapabilities=setOf(requires))
        }
        rows += listOf(PluginSettings("first",true),PluginSettings("second",true))
        assertThat(PluginRegistry(listOf(plugin("first","first.api","second.api"),plugin("second","second.api","first.api")),settings).available("ADMIN")).isEmpty()
    }
    @Test fun `Microsoft shared credentials change required fields without revealing secrets`() {
        val plugin = MicrosoftCalendarPlugin()
        assertThat(plugin.missingConfiguration(mapOf("mailbox" to "user@example.test"))).containsExactly("Directory (tenant) ID","Application (client) ID","Client secret")
        assertThat(plugin.missingConfiguration(mapOf("mailbox" to "user@example.test","credentialPlugin" to "onedrive"))).isEmpty()
        assertThat(registry.installed().first { it.manifest.id == "github" }.allowedRoles).containsExactly("ADMIN")
    }
}
