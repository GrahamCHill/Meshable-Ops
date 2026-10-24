package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import com.starter.plugins.kanban.KanbanBoard
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class ProjectAccessTest {
    private val mapper = jacksonObjectMapper()
    private val projects = mock(ProjectRepository::class.java)
    private val groups = mock(WorkGroupRepository::class.java)
    private val actors = mock(PluginActor::class.java)
    private val features = mock(AccessControlService::class.java)
    private val access = ProjectAccess(projects, groups, actors, features, mapper)
    private val lead = User(email = "lead@example.test")
    private val a = User(email = "a@example.test")
    private val b = User(email = "b@example.test")
    private val outsider = User(email = "outside@example.test")
    private val department = WorkGroupRecord(name = "Technology", kind = GroupKind.DEPARTMENT, detailsJson = mapper.writeValueAsString(WorkGroupData(setOf(a.id, b.id, outsider.id))))
    private val teamA = WorkGroupRecord(name = "Team A", detailsJson = mapper.writeValueAsString(WorkGroupData(setOf(a.id))))
    private val teamB = WorkGroupRecord(name = "Team B", detailsJson = mapper.writeValueAsString(WorkGroupData(setOf(b.id))))
    private val project = ProjectRecord(ownerId = outsider.id, leadId = lead.id, detailsJson = mapper.writeValueAsString(ProjectDetails(groupIds = setOf(teamA.id, teamB.id))))
    init {
        listOf(lead, a, b, outsider).forEach { `when`(features.features(it)).thenReturn(setOf("projects", "plugin.kanban")); `when`(actors.current(it.email)).thenReturn(it) }
        `when`(projects.findById(project.id)).thenReturn(Optional.of(project))
        `when`(groups.findAllById(setOf(teamA.id, teamB.id))).thenReturn(listOf(teamA, teamB))
        `when`(groups.findAllById(setOf(teamA.id))).thenReturn(listOf(teamA))
        `when`(groups.findAllById(setOf(department.id))).thenReturn(listOf(department))
    }
    @Test fun `department membership and creator metadata do not expose other team projects`() {
        assertThat(access.role(a, project)).isEqualTo(ProjectRole.MEMBER)
        assertThat(access.role(b, project)).isEqualTo(ProjectRole.MEMBER)
        assertThat(access.role(outsider, project)).isNull()
        assertThatThrownBy { access.require(project.id, outsider) }.isInstanceOf(NotFoundException::class.java)
        project.detailsJson = mapper.writeValueAsString(ProjectDetails(groupIds = setOf(department.id)))
        assertThat(access.role(a, project)).isNull()
        assertThat(access.role(lead, project)).isEqualTo(ProjectRole.TEAM_LEAD)
    }
    @Test fun `ordinary members cannot change categories or manage project settings`() {
        assertThatThrownBy { access.require(project.id, a, true) }.isInstanceOf(ForbiddenException::class.java)
        access.require(project.id, lead, true)
        teamA.projectRole = ProjectRole.MANAGER
        access.require(project.id, a, true)
    }
    @Test fun `boards can isolate teams within one project and revocations are immediate`() {
        val boardAccess = ProjectBoardAccess(access, mock(UserRepository::class.java), mock(JdbcTemplate::class.java), mapper, groups)
        val board = KanbanBoard(ownerId = b.id, projectId = project.id, teamIdsJson = mapper.writeValueAsString(setOf(teamA.id)))
        assertThat(boardAccess.readable(a, board)).isTrue()
        assertThat(boardAccess.readable(b, board)).isFalse()
        assertThat(boardAccess.readable(lead, board)).isTrue()
        teamA.detailsJson = mapper.writeValueAsString(WorkGroupData())
        assertThat(boardAccess.readable(a, board)).isFalse()
    }
    @Test fun `feature restrictions and external account restrictions apply before role bypasses`() {
        val admin = User(email = "admin@example.test", role = "ADMIN")
        `when`(features.features(admin)).thenReturn(emptySet())
        assertThat(access.role(admin, project)).isNull()
        val external = User(email = "client@example.test", role = "EXTERNAL")
        `when`(features.features(external)).thenReturn(setOf("projects"))
        assertThat(access.role(external, project)).isNull()
    }
}
