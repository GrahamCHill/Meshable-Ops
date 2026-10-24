package com.starter.plugins

import com.starter.exception.*
import com.starter.model.postgres.*
import com.starter.plugins.kanban.KanbanPlugin
import com.starter.repository.postgres.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class PluginAccessTest {
    private val users = mock(UserRepository::class.java)
    private val settings = mock(PluginSettingsRepository::class.java)
    private val access = PluginAccessInterceptor(PluginRegistry(listOf(KanbanPlugin()), settings), PluginActor(users))
    @AfterEach fun clear() = SecurityContextHolder.clearContext()

    @Test fun `direct plugin URLs recheck enabled state and the current database role`() {
        val row = PluginSettings("kanban", true, "ADMIN,USER")
        val user = User(email = "user@example.test", role = "USER")
        `when`(users.findByEmail(user.email)).thenReturn(Optional.of(user))
        `when`(settings.findById("kanban")).thenReturn(Optional.of(row))
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user.email, "")
        val request = MockHttpServletRequest("GET", "/api/plugins/kanban/boards")
        assertThat(access.preHandle(request, MockHttpServletResponse(), Any())).isTrue()
        row.allowedRolesCsv = "ADMIN"
        assertThatThrownBy { access.preHandle(request, MockHttpServletResponse(), Any()) }.isInstanceOf(NotFoundException::class.java)
        row.allowedRolesCsv = "ADMIN,USER"; row.enabled = false
        assertThatThrownBy { access.preHandle(request, MockHttpServletResponse(), Any()) }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `inactive or missing accounts cannot use plugins`() {
        val user = User(email = "user@example.test", active = false)
        `when`(users.findByEmail(user.email)).thenReturn(Optional.of(user))
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user.email, "")
        assertThatThrownBy { access.preHandle(MockHttpServletRequest("GET", "/api/plugins/kanban/boards"), MockHttpServletResponse(), Any()) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(settings)
    }
}
