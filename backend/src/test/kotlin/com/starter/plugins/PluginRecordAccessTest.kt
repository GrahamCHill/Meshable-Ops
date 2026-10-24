package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.plugins.packages.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class PluginRecordAccessTest {
    private val registry = mock(PluginRegistry::class.java)
    private val packages = mock(InstalledPluginCatalogue::class.java)
    private val jdbc = mock(JdbcTemplate::class.java)
    private val mapper = jacksonObjectMapper()
    private val service = PluginRecordService(registry, packages, jdbc, mapper)
    private val member = User(email = "member@example.test")
    private val input = PluginRecordInput(mapper.readTree("""{"title":"Private"}"""))
    @Test fun `bundled tables cannot be reached through generic managed record endpoints`() {
        assertThatThrownBy { service.list("kanban", "plugin_kanban_boards", member, 0) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(registry, jdbc)
    }
    @Test fun `table read and write guards execute before any managed SQL`() {
        `when`(packages.contains("zip-notes")).thenReturn(true)
        doThrow(ForbiddenException("No table access")).`when`(registry).requireTable("zip-notes", "users", member.role, false)
        doThrow(ForbiddenException("Read only")).`when`(registry).requireTable("zip-notes", "plugin_zip_notes_notes", member.role, true)
        assertThatThrownBy { service.list("zip-notes", "users", member, 0) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.create("zip-notes", "plugin_zip_notes_notes", input, member) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.update("zip-notes", "plugin_zip_notes_notes", UUID.randomUUID(), input, member) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.delete("zip-notes", "plugin_zip_notes_notes", UUID.randomUUID(), 0, member) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(jdbc)
    }
}
