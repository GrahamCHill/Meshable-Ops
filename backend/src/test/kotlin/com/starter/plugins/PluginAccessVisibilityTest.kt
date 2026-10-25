package com.starter.plugins

import com.starter.access.*
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.documents.DocumentsPlugin
import com.starter.plugins.packages.InstalledPluginCatalogue
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class PluginAccessVisibilityTest {
    private val settings=mock(PluginSettingsRepository::class.java)
    private val documents=DocumentsPlugin()
    private val definitions=listOf(documents)
    private val catalogue=AccessCatalogue(definitions)
    private val registry=PluginRegistry(definitions,settings)
    private val row=PluginSettings("documents",true)
    private val policy=AccessPolicy(roleFeatures=mapOf("USER" to setOf("storage","documents")),members=mapOf(UUID.randomUUID() to MemberAccess(setOf("documents"))))
    private val view=AccessPolicyView(7,policy,catalogue.features)
    init { `when`(settings.findAll()).thenReturn(listOf(row)) }

    @Test fun `disabled bundled pages and admin paths disappear while saved grants survive`() {
        assertThat(registry.effectiveAccessControls(view).features.map {it.path}).contains("/documents","/admin/document-templates","/projects")
        row.enabled=false
        val filtered=registry.effectiveAccessControls(view)
        assertThat(filtered.features.map {it.path}).doesNotContain("/documents","/admin/document-templates").contains("/projects","/admin/plugins")
        assertThat(filtered.revision).isEqualTo(7)
        assertThat(filtered.policy).isSameAs(policy)
        row.enabled=true
        assertThat(registry.effectiveAccessControls(filtered.copy(features=catalogue.features)).features).isEqualTo(view.features)
        verify(settings,never()).saveAndFlush(any(PluginSettings::class.java))
    }
    @Test fun `removed and dependency-blocked plugins have no configurable paths`() {
        val child=object:WorkspacePlugin {override val manifest=PluginManifest("dependent","Dependent","1.0.0","",requires=setOf("documents"))}
        val dependent=PluginSettings("dependent",true)
        `when`(settings.findAll()).thenReturn(listOf(row,dependent))
        val plugins=PluginRegistry(definitions+child,settings)
        val controls=view.copy(features=catalogue.features+child.accessViews)
        assertThat(plugins.effectiveAccessControls(controls).features.map {it.id}).contains("documents","plugin.dependent")
        row.enabled=false
        assertThat(plugins.effectiveAccessControls(controls).features.map {it.id}).doesNotContain("documents","admin.templates","plugin.dependent")
        row.enabled=true;row.removed=true
        assertThat(plugins.effectiveAccessControls(controls).features.map {it.id}).doesNotContain("documents","admin.templates","plugin.dependent")
    }
    @Test fun `uploaded views follow activation security and role availability`() {
        val packages=mock(InstalledPluginCatalogue::class.java)
        val manifest=PluginManifest("uploaded","Uploaded","1.0.0","")
        `when`(packages.manifests()).thenReturn(listOf(manifest))
        `when`(packages.contains("uploaded")).thenReturn(true)
        `when`(packages.allowed()).thenReturn(true)
        val uploaded=PluginSettings("uploaded",false)
        `when`(settings.findAll()).thenReturn(listOf(uploaded))
        val plugins=PluginRegistry(emptyList(),settings,packages=packages)
        val feature=AccessFeature("plugin.uploaded","Uploaded","/plugins/uploaded")
        val controls=AccessPolicyView(0,AccessPolicy(),listOf(feature))
        val me=MyAccess(setOf(feature.id),"USER",listOf(feature))
        assertThat(plugins.effectiveAccessControls(controls).features).isEmpty()
        assertThat(plugins.effectiveAccess(me).features).isEmpty()
        assertThat(plugins.effectiveAccess(me).views).isEmpty()
        uploaded.enabled=true
        assertThat(plugins.effectiveAccessControls(controls).features).containsExactly(feature)
        assertThat(plugins.effectiveAccess(me).views).containsExactly(feature)
        uploaded.allowedRolesCsv="ADMIN"
        assertThat(plugins.effectiveAccess(me).views).isEmpty()
        assertThat(plugins.effectiveAccessControls(controls).features).containsExactly(feature)
        `when`(packages.allowed()).thenReturn(false)
        assertThat(plugins.effectiveAccessControls(controls).features).isEmpty()
    }
    @Test fun `permissions being edited do not hide active views from access controls`() {
        val access=mock(AccessControlService::class.java)
        val plugins=PluginRegistry(definitions,settings,access)
        `when`(access.allows(anyString())).thenReturn(false)
        assertThat(plugins.effectiveAccessControls(view).features.map {it.id}).contains("documents","admin.templates")
        assertThat(plugins.effectiveAccess(MyAccess(setOf("storage","documents"),"USER",view.features)).views.map {it.id}).doesNotContain("documents","admin.templates")
    }
    @Test fun `both access-controls read and save responses filter disabled paths`() {
        row.enabled=false
        val access=mock(AccessControlService::class.java)
        val request=AccessPolicyRequest(view.revision,policy)
        `when`(access.view()).thenReturn(view)
        `when`(access.save(request)).thenReturn(view)
        val controller=AccessController(access,registry)
        assertThat(controller.view().features.map {it.id}).doesNotContain("documents","admin.templates")
        val saved=controller.save(request)
        assertThat(saved.features.map {it.id}).doesNotContain("documents","admin.templates")
        assertThat(saved.policy).isSameAs(policy)
        verify(access).save(request)
    }
}
