package com.starter.plugins.knowledge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.projects.ProjectReportCoverService
import com.starter.projects.ReportCoverContext
import com.starter.rendering.WeasyRendererClient
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class KnowledgeSettingsTest {
    private val rows = mock(KnowledgeSettingsRepository::class.java)
    private val access = mock(KnowledgeAccess::class.java)
    private val users = mock(UserRepository::class.java)
    private val renderer = mock(WeasyRendererClient::class.java)
    private val covers = mock(ProjectReportCoverService::class.java)
    private val actor = User(email = "member@example.test")
    private val row = KnowledgeSettings(revision = 2, exporterIdsJson = "[\"${actor.id}\"]")
    private val service = KnowledgeSettingsService(rows, access, users, jacksonObjectMapper(), renderer, covers, mock(JdbcTemplate::class.java))
    init {
        `when`(rows.findById(1)).thenReturn(Optional.of(row)); `when`(users.findById(actor.id)).thenReturn(Optional.of(actor))
        `when`(users.findAll()).thenReturn(listOf(actor)); `when`(access.exporters(row)).thenReturn(setOf(actor.id))
    }
    @Test fun `administrators can revoke exporters without relying on PDF rendering`() {
        service.permissions("admin", KnowledgePermissionsInput(emptySet(), 2))
        assertThat(row.exporterIdsJson).isEqualTo("[]")
        verify(rows).saveAndFlush(row)
        verifyNoInteractions(renderer)
    }
    @Test fun `permission updates require administration an active target and a fresh revision`() {
        doThrow(ForbiddenException("Admin only")).`when`(access).admin("member")
        assertThatThrownBy { service.permissions("member", KnowledgePermissionsInput(emptySet(), 2)) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.permissions("admin", KnowledgePermissionsInput(emptySet(), 1)) }.isInstanceOf(ConflictException::class.java)
        actor.active = false
        assertThatThrownBy { service.permissions("admin", KnowledgePermissionsInput(setOf(actor.id), 2)) }.isInstanceOf(BadRequestException::class.java)
        verify(rows, never()).saveAndFlush(any(KnowledgeSettings::class.java))
    }
    @Test fun `cover saves preserve exporter grants even when an account is inactive`() {
        actor.active = false
        `when`(covers.context(null)).thenReturn(ReportCoverContext("", emptyMap(), "Workspace", "Company", ""))
        `when`(renderer.reportCover(anyMap<String, Any>())).thenReturn(byteArrayOf())
        val original = row.exporterIdsJson
        service.save("admin", KnowledgeSettingsInput("<h1>{{title}}</h1>", emptyMap(), 2))
        assertThat(row.exporterIdsJson).isEqualTo(original)
        assertThat(row.coverHtml).isEqualTo("<h1>{{title}}</h1>")
        verify(users, never()).findById(actor.id)
    }
    @Test fun `inactive and deleted exporter grants remain visible for individual revocation`() {
        actor.active = false
        assertThat(service.view("admin").members.single().active).isFalse()
        `when`(users.findAll()).thenReturn(emptyList())
        val missing = service.view("admin").members.single()
        assertThat(missing.id).isEqualTo(actor.id)
        assertThat(missing.active).isFalse()
    }
}
