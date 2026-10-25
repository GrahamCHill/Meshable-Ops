package com.starter.plugins.knowledge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import com.starter.plugins.PluginRegistry
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class KnowledgeAccessTest {
    private val actors = mock(PluginActor::class.java)
    private val plugins = mock(PluginRegistry::class.java)
    private val settings = mock(KnowledgeSettingsRepository::class.java)
    private val mapper = jacksonObjectMapper()
    private val actor = User(email = "member@example.test")
    private val access = KnowledgeAccess(actors, plugins, settings, mapper)
    init { `when`(actors.fresh(actor.email)).thenReturn(actor); `when`(settings.findById(1)).thenReturn(Optional.empty()) }

    @Test fun `readers and unlisted administrators cannot export by default`() {
        listOf("USER", "ADMIN", "SUPER_ADMIN").forEach { role ->
            actor.role = role
            assertThat(access.status(actor.email).canExport).isFalse()
            assertThatThrownBy { access.requireExport(actor.email) }.isInstanceOf(ForbiddenException::class.java)
        }
    }
    @Test fun `only an explicitly selected account can export and revocation is immediate`() {
        val row = KnowledgeSettings(exporterIdsJson = mapper.writeValueAsString(setOf(actor.id)))
        `when`(settings.findById(1)).thenReturn(Optional.of(row))
        assertThat(access.requireExport(actor.email)).isSameAs(row)
        verify(plugins).requireTable("knowledge", KnowledgePlugin.PAGES, actor.role, false)
        row.exporterIdsJson = "[]"
        assertThatThrownBy { access.requireExport(actor.email) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `member administration and inactive or disabled plugin access fail closed`() {
        assertThatThrownBy { access.admin(actor.email) }.isInstanceOf(ForbiddenException::class.java)
        actor.role = "ADMIN"; access.admin(actor.email)
        verify(plugins).requireTable("knowledge", KnowledgePlugin.SETTINGS, "ADMIN", true)
        doThrow(ForbiddenException("Disabled")).`when`(plugins).requireTable("knowledge", KnowledgePlugin.PAGES, "ADMIN", false)
        assertThatThrownBy { access.requireExport(actor.email) }.isInstanceOf(ForbiddenException::class.java)
        `when`(actors.fresh(actor.email)).thenThrow(ForbiddenException("Inactive"))
        assertThatThrownBy { access.status(actor.email) }.isInstanceOf(ForbiddenException::class.java)
    }
}
