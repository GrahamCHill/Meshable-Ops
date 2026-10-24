package com.starter.plugins

import com.starter.access.*
import com.starter.exception.*
import com.starter.model.postgres.PluginSettings
import com.starter.model.postgres.User
import com.starter.plugins.documents.DocumentsPlugin
import com.starter.plugins.workflows.WorkflowsPlugin
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class OptionalModulesTest {
    private val rows = mock(PluginSettingsRepository::class.java)
    private val controls = mock(AccessControlService::class.java)
    private val saved = listOf(PluginSettings("documents", true), PluginSettings("workflows", true))
    private val definitions = listOf(DocumentsPlugin(), WorkflowsPlugin())
    private val registry = PluginRegistry(definitions, rows, controls)
    init {
        `when`(rows.findAll()).thenReturn(saved)
        saved.forEach { `when`(rows.findById(it.pluginId)).thenReturn(Optional.of(it)) }
        `when`(controls.allows(anyString())).thenReturn(true)
    }
    @Test fun `separate optional modules retain routes permissions tables and runtime ownership`() {
        assertThat(definitions.flatMap { it.accessViews }.map { it.path }).containsExactly("/documents", "/admin/document-templates", "/workflows", "/admin/workflows")
        assertThat(DocumentsPlugin().manifest.runtimes).containsExactlyInAnyOrder(PluginRuntime.REACT, PluginRuntime.KOTLIN, PluginRuntime.PYTHON)
        assertThat(WorkflowsPlugin().manifest.requires).isEmpty()
        assertThat(WorkflowsPlugin().manifest.extends).contains("documents")
        saved[0].enabled = false
        assertThat(registry.available("USER").map { it.manifest.id }).containsExactly("workflows")
        assertThat(registry.available("USER").single().features.single().available).isFalse()
        assertThatThrownBy { registry.requireAvailable("documents", "USER") }.isInstanceOf(NotFoundException::class.java)
        registry.requireAvailable("workflows", "USER")
    }
    @Test fun `effective navigation hides disabled modules while explicit existing RBAC IDs survive reenable`() {
        val access = MyAccess(setOf("storage", "documents", "admin.templates", "workflows", "admin.workflows"), "ADMIN", definitions.flatMap { it.accessViews })
        assertThat(registry.effectiveAccess(access).features).isEqualTo(access.features)
        saved[0].enabled = false
        assertThat(registry.effectiveAccess(access).features).containsExactlyInAnyOrder("storage", "workflows", "admin.workflows")
        saved[0].enabled = true
        assertThat(registry.effectiveAccess(access).features).isEqualTo(access.features)
        verify(rows, never()).saveAndFlush(any(PluginSettings::class.java))
    }
    @Test fun `delegated template authors keep access without acquiring a personal document library`() {
        `when`(controls.allows("documents")).thenReturn(false)
        registry.requireAvailable("documents", "ADMIN")
        assertThat(registry.available("ADMIN").map { it.manifest.id }).contains("documents")
        `when`(controls.allows("admin.templates")).thenReturn(false)
        assertThatThrownBy { registry.requireAvailable("documents", "ADMIN") }.isInstanceOf(NotFoundException::class.java)
        // A published workflow grants only its reached PDFs; it does not grant the private library route.
        registry.requireTable("documents", "generated_documents", "USER", supporting = true)
        saved[0].enabled = false
        assertThatThrownBy { registry.requireTable("documents", "generated_documents", "USER", supporting = true) }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `table policies and explicit member features cannot be bypassed by service calls`() {
        registry.requireTable("documents", "generated_documents", "USER", write = true)
        assertThatThrownBy { registry.requireTable("documents", "document_templates", "USER", write = true) }.isInstanceOf(ForbiddenException::class.java)
        saved[0].allowedRolesCsv = "ADMIN"
        assertThatThrownBy { registry.requireTable("documents", "generated_documents", "USER") }.isInstanceOf(NotFoundException::class.java)
        val member = User()
        `when`(controls.features(member)).thenReturn(setOf("workflows"))
        assertThatThrownBy { BundledModuleAccess(registry, controls).check("documents", member, "generated_documents", "documents") }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `legacy owned tables are unavailable to uploaded manifests and module guards match exact API roots`() {
        assertThatThrownBy { PluginManifests.validate(DocumentsPlugin().manifest) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PluginManifests.validate(WorkflowsPlugin().manifest) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(BundledModuleGuard.owner("/api/documents/123/download")).isEqualTo("documents")
        assertThat(BundledModuleGuard.owner("/api/admin/document-templates")).isEqualTo("documents")
        assertThat(BundledModuleGuard.owner("/api/workflows/runs/123/advance")).isEqualTo("workflows")
        assertThat(BundledModuleGuard.owner("/api/admin/workflows")).isEqualTo("workflows")
        listOf("/api/document-access", "/api/admin/documents", "/api/workflows-extra", "/api/projects/reports").forEach { assertThat(BundledModuleGuard.owner(it)).isNull() }
    }
    @Test fun `required supporting plugins still respect member feature restrictions`() {
        val dependent = object : WorkspacePlugin { override val manifest = PluginManifest("dependent", "Dependent", "1.0.0", "Requires document support", requires = setOf("documents")) }
        val row = PluginSettings("dependent", true)
        `when`(rows.findAll()).thenReturn(saved + row)
        `when`(rows.findById(row.pluginId)).thenReturn(Optional.of(row))
        val host = PluginRegistry(definitions + dependent, rows, controls)
        host.requireAvailable("dependent", "USER")
        `when`(controls.allows("documents")).thenReturn(false)
        `when`(controls.allows("admin.templates")).thenReturn(false)
        assertThatThrownBy { host.requireAvailable("dependent", "USER") }.isInstanceOf(NotFoundException::class.java)
        assertThat(host.available("USER").map { it.manifest.id }).doesNotContain("dependent")
    }
}
