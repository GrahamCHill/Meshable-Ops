package com.starter.plugins

import com.starter.plugins.kanban.*
import com.starter.projects.ProjectAccess
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.access.AccessControlService
import org.springframework.jdbc.core.JdbcTemplate
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.UUID
import java.util.Optional

class KanbanEstimationTest {
    @Test fun `project overrides can enable either both or no estimate types and inheritance restores organisation settings`() {
        val configuration=mock(PluginConfigurationService::class.java);val records=mock(ProjectEstimationRepository::class.java)
        val id=UUID.randomUUID();val row=ProjectEstimation(id,inherit=false,storyPoints=false,tshirtSizes=true)
        `when`(configuration.current("kanban")).thenReturn(mapOf("storyPoints" to "true","tshirtSizes" to "false"))
        `when`(records.findById(id)).thenReturn(Optional.of(row))
        val service=KanbanEstimationService(configuration,records,mock(ProjectAccess::class.java),mock(PluginRegistry::class.java),mock(JdbcTemplate::class.java),mock(AccessControlService::class.java))
        assertThat(service.effective(null)).isEqualTo(KanbanEstimation(true,false))
        assertThat(service.effective(id)).isEqualTo(KanbanEstimation(false,true))
        row.storyPoints=true;assertThat(service.effective(id)).isEqualTo(KanbanEstimation(true,true))
        row.storyPoints=false;row.tshirtSizes=false;assertThat(service.effective(id)).isEqualTo(KanbanEstimation(false,false))
        row.inherit=true;assertThat(service.effective(id)).isEqualTo(KanbanEstimation(true,false))
    }
    @Test fun `stale project estimate changes are rejected before persistence`() {
        val configuration=mock(PluginConfigurationService::class.java);val records=mock(ProjectEstimationRepository::class.java)
        val projects=mock(ProjectAccess::class.java);val user=User(email="admin@example.test",role="ADMIN");val id=UUID.randomUUID()
        `when`(projects.actor(user.email)).thenReturn(user);`when`(records.findById(id)).thenReturn(Optional.of(ProjectEstimation(id,revision=3)))
        val service=KanbanEstimationService(configuration,records,projects,mock(PluginRegistry::class.java),mock(JdbcTemplate::class.java),mock(AccessControlService::class.java))
        assertThatThrownBy { service.save(user.email,id,ProjectEstimationInput(revision=2)) }.isInstanceOf(ConflictException::class.java)
        verify(projects).require(id,user,true);verify(records,never()).saveAndFlush(any(ProjectEstimation::class.java))
    }
    @ParameterizedTest @ValueSource(strings=["USER","BUSINESS_MANAGER","EXTERNAL"])
    fun `non administrators cannot read or save project estimation settings`(role: String) {
        val projects=mock(ProjectAccess::class.java);val user=User(email="member@example.test",role=role);val id=UUID.randomUUID()
        val records=mock(ProjectEstimationRepository::class.java);val registry=mock(PluginRegistry::class.java);val jdbc=mock(JdbcTemplate::class.java)
        `when`(projects.actor(user.email)).thenReturn(user)
        val service=KanbanEstimationService(mock(PluginConfigurationService::class.java),records,projects,registry,jdbc,mock(AccessControlService::class.java))
        assertThatThrownBy { service.view(user.email,id) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.save(user.email,id,ProjectEstimationInput()) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(records,registry,jdbc)
    }
    @ParameterizedTest @ValueSource(strings=["ADMIN","SUPER_ADMIN"])
    fun `administrators can read and save project overrides`(role: String) {
        val projects=mock(ProjectAccess::class.java);val user=User(email="admin@example.test",role=role);val id=UUID.randomUUID()
        val records=mock(ProjectEstimationRepository::class.java);val row=ProjectEstimation(id,revision=3)
        val registry=mock(PluginRegistry::class.java)
        `when`(projects.actor(user.email)).thenReturn(user);`when`(records.findById(id)).thenReturn(Optional.of(row))
        val service=KanbanEstimationService(mock(PluginConfigurationService::class.java),records,projects,registry,mock(JdbcTemplate::class.java),mock(AccessControlService::class.java))
        val result=service.save(user.email,id,ProjectEstimationInput(inherit=false,storyPoints=false,tshirtSizes=true,revision=3))
        assertThat(result).isEqualTo(ProjectEstimationView(false,false,true,3,true))
        verify(projects).require(id,user,true);verify(projects).require(id,user)
        verify(registry).requireTable("kanban","plugin_kanban_estimation",role,true)
        verify(registry).requireTable("kanban","plugin_kanban_estimation",role)
        verify(records).saveAndFlush(row)
    }
    @Test fun `administrator without plugin administration cannot bypass the settings menu through the API`() {
        val projects=mock(ProjectAccess::class.java);val user=User(email="limited-admin@example.test",role="ADMIN");val id=UUID.randomUUID()
        val records=mock(ProjectEstimationRepository::class.java);val registry=mock(PluginRegistry::class.java);val jdbc=mock(JdbcTemplate::class.java)
        val access=mock(AccessControlService::class.java)
        `when`(projects.actor(user.email)).thenReturn(user)
        doThrow(ForbiddenException("Plugin administration unavailable")).`when`(access).requireFeature("admin.plugins")
        val service=KanbanEstimationService(mock(PluginConfigurationService::class.java),records,projects,registry,jdbc,access)
        assertThatThrownBy {service.view(user.email,id)}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {service.save(user.email,id,ProjectEstimationInput())}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(records,registry,jdbc)
        verify(projects,never()).require(id,user,true)
    }
}
