package com.starter.projects

import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.sourcecontrol.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import java.util.UUID

class ProjectRepositoryLinksTest {
    private val rows=mock(ProjectRepositoryLinkRepository::class.java)
    private val access=mock(ProjectAccess::class.java)
    private val source=mock(SourceControlService::class.java)
    private val service=ProjectRepositoryLinks(rows,access,source,mock(JdbcTemplate::class.java))
    private val actor=User(email="member@example.test")
    private val project=UUID.randomUUID()
    private val row=ProjectRepositoryLink(projectId=project,providerId="github",repository="team/repo",title="Private title",url="https://github.com/team/repo",configurationHash="current",revision=3)
    init {
        `when`(access.actor(actor.email)).thenReturn(actor)
        `when`(rows.findByProjectId(project)).thenReturn(listOf(row))
        `when`(rows.findById(row.id)).thenReturn(Optional.of(row))
        `when`(source.fingerprint("github",actor.role)).thenReturn("current")
    }
    @Test fun `provider restrictions or changed configuration hide saved metadata`() {
        assertThat(service.list(actor.email,project).single().title).isEqualTo("Private title")
        `when`(source.fingerprint("github",actor.role)).thenReturn("rotated")
        val hidden=service.list(actor.email,project).single()
        assertThat(hidden.available).isFalse();assertThat(hidden.repository).isNull();assertThat(hidden.url).isNull();assertThat(hidden.title).doesNotContain("Private")
        `when`(source.fingerprint("github",actor.role)).thenThrow(NotFoundException("Disabled"))
        assertThat(service.list(actor.email,project).single().available).isFalse()
    }
    @Test fun `project membership is checked before reading links and managers are required for writes`() {
        doThrow(NotFoundException("Project unavailable")).`when`(access).require(project,actor,false)
        assertThatThrownBy {service.list(actor.email,project)}.isInstanceOf(NotFoundException::class.java)
        verify(rows,never()).findByProjectId(project)
        doThrow(ForbiddenException("Read only")).`when`(access).require(project,actor,true)
        assertThatThrownBy {service.add(actor.email,project,ProjectRepositoryInput("github","team/repo"))}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(source)
    }
    @Test fun `stale and cross-project deletions fail while disabled provider links remain removable`() {
        assertThatThrownBy {service.remove(actor.email,project,row.id,2)}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.remove(actor.email,UUID.randomUUID(),row.id,3)}.isInstanceOf(NotFoundException::class.java)
        service.remove(actor.email,project,row.id,3)
        verify(rows).delete(row);verifyNoInteractions(source)
    }
}
