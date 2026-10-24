package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import com.starter.plugins.kanban.*
import com.starter.plugins.sourcecontrol.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import java.util.UUID

class ProjectDevelopmentTest {
    private val rows=mock(ProjectDevelopmentLinkRepository::class.java)
    private val repositories=mock(ProjectRepositoryLinkRepository::class.java)
    private val settings=mock(ProjectDevelopmentSettingsService::class.java)
    private val access=mock(ProjectAccess::class.java)
    private val source=mock(SourceControlService::class.java)
    private val boards=mock(KanbanRepository::class.java)
    private val boardAccess=mock(ProjectBoardAccess::class.java)
    private val plugins=mock(PluginRegistry::class.java)
    private val actor=User(email="manager@example.test")
    private val project=UUID.randomUUID()
    private val repo=ProjectRepositoryLink(projectId=project,providerId="github",repository="team/repo")
    private val link=ProjectDevelopmentLink(projectId=project,repositoryId=repo.id,reference="feature/portal",title="Private branch",url="https://github.com/team/repo/tree/feature%2Fportal",configurationHash="current",revision=4)
    private val service=ProjectDevelopmentService(rows,repositories,settings,access,source,boards,boardAccess,plugins,jacksonObjectMapper().findAndRegisterModules(),mock(JdbcTemplate::class.java))
    init {
        `when`(access.actor(actor.email)).thenReturn(actor)
        `when`(settings.effective(project)).thenReturn(DevelopmentFeatures(true,true,true,true))
        `when`(repositories.findById(repo.id)).thenReturn(Optional.of(repo))
        `when`(repositories.findByProjectId(project)).thenReturn(listOf(repo))
        `when`(rows.findById(link.id)).thenReturn(Optional.of(link))
        `when`(rows.findByProjectId(project)).thenReturn(listOf(link))
        `when`(source.fingerprint("github",actor.role)).thenReturn("current")
        `when`(source.trackedResource("github","team/repo","BRANCH","feature/new",actor.role,null,"","unknown")).thenReturn(TrackedDevelopmentResource("feature/new","https://github.com/team/repo/tree/feature%2Fnew","active"))
        `when`(rows.saveAndFlush(any(ProjectDevelopmentLink::class.java))).thenAnswer {it.arguments[0]}
    }
    @Test fun `project membership and disabled features are enforced before provider calls`() {
        doThrow(NotFoundException("Unavailable")).`when`(access).require(project,actor,false)
        assertThatThrownBy {service.list(actor.email,project)}.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(repositories,source)
        `when`(settings.effective(project)).thenReturn(DevelopmentFeatures(branches=false))
        assertThatThrownBy {service.add(actor.email,project,ProjectDevelopmentInput(repo.id,"BRANCH","feature/new"))}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(source)
    }
    @Test fun `changed provider configuration redacts metadata and disabled kinds disappear`() {
        assertThat(service.list(actor.email,project).single().title).isEqualTo("Private branch")
        `when`(source.fingerprint("github",actor.role)).thenReturn("rotated")
        val hidden=service.list(actor.email,project).single()
        assertThat(hidden.available).isFalse();assertThat(hidden.title).doesNotContain("Private");assertThat(hidden.url).isNull();assertThat(hidden.reference).isNull()
        `when`(settings.effective(project)).thenReturn(DevelopmentFeatures(branches=false))
        assertThat(service.list(actor.email,project)).isEmpty()
    }
    @Test fun `board links require the same project and current board visibility`() {
        val board=KanbanBoard(projectId=project)
        `when`(boards.findById(board.id)).thenReturn(Optional.of(board))
        `when`(boardAccess.readable(actor,board)).thenReturn(false)
        assertThatThrownBy {service.add(actor.email,project,ProjectDevelopmentInput(repo.id,"BRANCH","feature/new",board.id))}.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(source)
        val scoped=ProjectDevelopmentLink(projectId=project,repositoryId=repo.id,boardId=board.id)
        `when`(rows.findByProjectId(project)).thenReturn(listOf(scoped))
        assertThat(service.list(actor.email,project)).isEmpty()
    }
    @Test fun `new resources stay linked to project repositories and retain board and ticket scope`() {
        val column=KanbanColumn(UUID.randomUUID(),"To do");val card=KanbanCard(UUID.randomUUID(),column.id,"Ticket")
        val board=KanbanBoard(projectId=project,contentJson=jacksonObjectMapper().writeValueAsString(KanbanContent(listOf(column),listOf(card))))
        `when`(boards.findById(board.id)).thenReturn(Optional.of(board));`when`(boardAccess.readable(actor,board)).thenReturn(true)
        val result=service.add(actor.email,project,ProjectDevelopmentInput(repo.id,"BRANCH","feature/new",board.id,card.id))
        assertThat(result.repositoryId).isEqualTo(repo.id);assertThat(result.boardId).isEqualTo(board.id);assertThat(result.cardId).isEqualTo(card.id);assertThat(result.url).contains("feature%2Fnew")
    }
    @Test fun `stale cross-project and changed-configuration writes fail`() {
        assertThatThrownBy {service.remove(actor.email,project,link.id,3)}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.remove(actor.email,UUID.randomUUID(),link.id,4)}.isInstanceOf(NotFoundException::class.java)
        `when`(source.fingerprint("github",actor.role)).thenReturn("current","rotated")
        assertThatThrownBy {service.add(actor.email,project,ProjectDevelopmentInput(repo.id,"BRANCH","feature/new"))}.isInstanceOf(ConflictException::class.java)
        verify(rows,never()).saveAndFlush(any(ProjectDevelopmentLink::class.java))
    }
    @Test fun `settings use optimistic revisions and preserve independent toggles`() {
        val records=mock(ProjectDevelopmentSettingsRepository::class.java)
        val projectRow=ProjectRecord(id=project)
        `when`(access.require(project,actor,false)).thenReturn(projectRow)
        `when`(access.role(actor,projectRow)).thenReturn(ProjectRole.MANAGER)
        `when`(records.findById(project)).thenReturn(Optional.empty())
        val service=ProjectDevelopmentSettingsService(records,access,mock(JdbcTemplate::class.java))
        assertThat(service.effective(project)).isEqualTo(DevelopmentFeatures())
        assertThatThrownBy {service.save(actor.email,project,DevelopmentSettingsInput(DevelopmentFeatures(),0))}.isInstanceOf(ConflictException::class.java)
        `when`(records.saveAndFlush(any(ProjectDevelopmentSettingsRecord::class.java))).thenAnswer {call->
            val row=call.arguments[0] as ProjectDevelopmentSettingsRecord;`when`(records.findById(project)).thenReturn(Optional.of(row));row
        }
        val result=service.save(actor.email,project,DevelopmentSettingsInput(DevelopmentFeatures(true,true,false,true),-1))
        assertThat(result.features.branches).isTrue();assertThat(result.features.pullRequests).isFalse();assertThat(result.features.deployments).isTrue()
        assertThat(result.revision).isEqualTo(0)
    }
    @Test fun `manual deployment states are revision checked and provider snapshots cannot be overwritten`() {
        val manual=ProjectDevelopmentLink(projectId=project,repositoryId=repo.id,kind="DEPLOYMENT",reference="7",manual=true,configurationHash="current",revision=2)
        `when`(rows.findById(manual.id)).thenReturn(Optional.of(manual))
        val result=service.status(actor.email,project,manual.id,ProjectDeploymentStatusInput(2,"success"))
        assertThat(result.state).isEqualTo("success")
        assertThatThrownBy {service.status(actor.email,project,manual.id,ProjectDeploymentStatusInput(1,"failed"))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.status(actor.email,project,link.id,ProjectDeploymentStatusInput(4,"success"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {service.status(actor.email,project,manual.id,ProjectDeploymentStatusInput(2,"invented"))}.isInstanceOf(BadRequestException::class.java)
    }
}
