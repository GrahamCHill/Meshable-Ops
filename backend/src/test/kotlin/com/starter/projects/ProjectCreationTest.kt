package com.starter.projects

import com.starter.access.*
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.model.postgres.PluginSettings
import com.starter.plugins.PluginRegistry
import com.starter.plugins.kanban.KanbanPlugin
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class ProjectCreationTest {
    @Test fun `Business Managers can create without administrator powers or plugin table bypasses`() {
        val catalogue = AccessCatalogue(emptyList())
        assertThat(catalogue.defaults(Roles.BUSINESS_MANAGER)).contains("projects", "storage")
        assertThat(catalogue.defaults(Roles.BUSINESS_MANAGER).filter { it.startsWith("admin.") || it == "events" }).isEmpty()
        assertThat(Roles.admin(Roles.BUSINESS_MANAGER)).isFalse()
        assertThat(Roles.plugin(Roles.BUSINESS_MANAGER)).isEqualTo("USER")
        val jdbc = mock(JdbcTemplate::class.java)
        assertThat(ProjectCreationRules.allowed(User(role = Roles.BUSINESS_MANAGER), jdbc)).isTrue()
        assertThat(ProjectCreationRules.allowed(User(role = "ADMIN"), jdbc)).isTrue()
        verifyNoInteractions(jdbc)
        val settings = mock(PluginSettingsRepository::class.java)
        `when`(settings.findById("kanban")).thenReturn(Optional.of(PluginSettings("kanban", true, "ADMIN,USER")))
        PluginRegistry(listOf(KanbanPlugin()), settings).requireTable("kanban", "plugin_kanban_boards", Roles.BUSINESS_MANAGER, true)
    }
    @Test fun `inactive external unknown and ordinary members cannot create`() {
        val jdbc = mock(JdbcTemplate::class.java)
        listOf(User(role = Roles.BUSINESS_MANAGER, active = false), User(role = "EXTERNAL"), User(role = "unknown"), User(role = "USER")).forEach {
            assertThat(ProjectCreationRules.allowed(it, jdbc)).isFalse()
            assertThatThrownBy { ProjectCreationRules.require(it, jdbc) }.isInstanceOf(ForbiddenException::class.java)
        }
    }
    @Test fun `members with current project leadership can create and revocation takes effect`() {
        var leadership = true
        val jdbc = mock(JdbcTemplate::class.java) { call ->
            if (call.method.name == "queryForObject") leadership else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
        }
        val actor = User()
        assertThat(ProjectCreationRules.allowed(actor, jdbc)).isTrue()
        leadership = false
        assertThat(ProjectCreationRules.allowed(actor, jdbc)).isFalse()
    }
}
