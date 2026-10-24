package com.starter.plugins

import com.starter.plugins.kanban.*
import com.starter.projects.ProjectAccess
import com.starter.exception.ConflictException
import com.starter.model.postgres.User
import org.springframework.jdbc.core.JdbcTemplate
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID
import java.util.Optional

class KanbanEstimationTest {
    @Test fun `project overrides can enable either both or no estimate types and inheritance restores organisation settings`() {
        val configuration=mock(PluginConfigurationService::class.java);val records=mock(ProjectEstimationRepository::class.java)
        val id=UUID.randomUUID();val row=ProjectEstimation(id,inherit=false,storyPoints=false,tshirtSizes=true)
        `when`(configuration.current("kanban")).thenReturn(mapOf("storyPoints" to "true","tshirtSizes" to "false"))
        `when`(records.findById(id)).thenReturn(Optional.of(row))
        val service=KanbanEstimationService(configuration,records,mock(ProjectAccess::class.java),mock(PluginRegistry::class.java),mock(JdbcTemplate::class.java))
        assertThat(service.effective(null)).isEqualTo(KanbanEstimation(true,false))
        assertThat(service.effective(id)).isEqualTo(KanbanEstimation(false,true))
        row.storyPoints=true;assertThat(service.effective(id)).isEqualTo(KanbanEstimation(true,true))
        row.storyPoints=false;row.tshirtSizes=false;assertThat(service.effective(id)).isEqualTo(KanbanEstimation(false,false))
        row.inherit=true;assertThat(service.effective(id)).isEqualTo(KanbanEstimation(true,false))
    }
    @Test fun `stale project estimate changes are rejected before persistence`() {
        val configuration=mock(PluginConfigurationService::class.java);val records=mock(ProjectEstimationRepository::class.java)
        val projects=mock(ProjectAccess::class.java);val user=User(email="manager@example.test");val id=UUID.randomUUID()
        `when`(projects.actor(user.email)).thenReturn(user);`when`(records.findById(id)).thenReturn(Optional.of(ProjectEstimation(id,revision=3)))
        val service=KanbanEstimationService(configuration,records,projects,mock(PluginRegistry::class.java),mock(JdbcTemplate::class.java))
        assertThatThrownBy { service.save(user.email,id,ProjectEstimationInput(revision=2)) }.isInstanceOf(ConflictException::class.java)
        verify(projects).require(id,user,true);verify(records,never()).saveAndFlush(any(ProjectEstimation::class.java))
    }
}
