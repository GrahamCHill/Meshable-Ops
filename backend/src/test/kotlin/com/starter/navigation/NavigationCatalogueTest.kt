package com.starter.navigation

import com.starter.access.AccessControlService
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.*
import com.starter.plugins.packages.InstalledPluginCatalogue
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class NavigationCatalogueTest {
    @Test fun `ordering catalogue hides disabled plugins including their extension links`() {
        fun module(id:String) = object:WorkspacePlugin {
            override val manifest=PluginManifest(id,id,"1.0.0","Navigation fixture",extensions=setOf(PluginExtension.PAGE),
                menuItems=listOf(PluginMenuItem("reports","Reports")),placements=listOf(PluginPlacement("navigation.links","Reports","reports")))
        }
        val modules=listOf(module("visible"),module("hidden"))
        val rows=mock(PluginSettingsRepository::class.java)
        val visible=PluginSettings("visible",true)
        `when`(rows.findAll()).thenReturn(listOf(visible,PluginSettings("hidden",false)))
        val packages=mock(InstalledPluginCatalogue::class.java)
        `when`(packages.manifests()).thenReturn(emptyList())
        val catalogue=NavigationCatalogue(modules,packages,PluginRegistry(modules,rows),mock(AccessControlService::class.java))
        assertThat(catalogue.all().filter {it.pluginId=="hidden"}).hasSize(2)
        assertThat(catalogue.configurable().mapNotNull {it.pluginId}).containsExactly("visible","visible")
        visible.enabled=false
        assertThat(catalogue.configurable().mapNotNull {it.pluginId}).isEmpty()
    }
}
