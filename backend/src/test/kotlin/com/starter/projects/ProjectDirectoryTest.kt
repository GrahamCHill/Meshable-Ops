package com.starter.projects

import com.starter.exception.*
import com.starter.model.postgres.User
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class ProjectDirectoryTest {
    private val actor=User(email="member@example.test");private val project=ProjectRecord(category="ACTIVE")
    private val rows=mock(ProjectRepository::class.java);private val access=mock(ProjectAccess::class.java);private val settings=mock(ProjectSettingsService::class.java);private val projects=mock(ProjectService::class.java)
    private val service=ProjectDirectory(rows,access,settings,projects,mock(JdbcTemplate::class.java))
    init {`when`(access.actor(actor.email)).thenReturn(actor);`when`(access.require(project.id,actor,false)).thenReturn(project);`when`(settings.current()).thenReturn(ProjectSettingsView(ProjectSettings(),0))}
    @Test fun `specific users and roles can move only projects they can access`() {
        assertThatThrownBy {service.move(actor.email,project.id,ProjectSectionMove("ARCHIVED",0))}.isInstanceOf(ForbiddenException::class.java)
        `when`(settings.current()).thenReturn(ProjectSettingsView(ProjectSettings(sectionMoveUserIds=setOf(actor.id)),0))
        service.move(actor.email,project.id,ProjectSectionMove("ARCHIVED",0));assertThat(project.category).isEqualTo("ARCHIVED")
        assertThatThrownBy {service.move(actor.email,project.id,ProjectSectionMove("ACTIVE",9))}.isInstanceOf(ConflictException::class.java)
        assertThat(ProjectSectionRules.canMove(actor,ProjectSettings(sectionMoveRoles=setOf("USER")))).isTrue()
        doThrow(NotFoundException("Unavailable")).`when`(access).require(project.id,actor,false)
        assertThatThrownBy {service.move(actor.email,project.id,ProjectSectionMove("ACTIVE",0))}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `pages carry client filters and never expose more than ten records`() {
        val client=UUID.randomUUID();val values=(1..11).map {ProjectSummary(UUID.randomUUID(),"Project $it",client,"Client","ACTIVE",emptySet(),Instant.now())}
        `when`(projects.list(actor.email,"portal","ACTIVE",emptySet(),client,10,11)).thenReturn(values)
        val result=service.page(actor.email,"portal","ACTIVE",client,emptySet(),10,10)
        assertThat(result.items).hasSize(10);assertThat(result.hasMore).isTrue();assertThat(result.nextOffset).isEqualTo(20)
        assertThatThrownBy {service.page(actor.email,"","ACTIVE",null,emptySet(),0,11)}.isInstanceOf(BadRequestException::class.java)
    }
}
