package com.starter.projects

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import com.starter.plugins.kanban.*
import com.starter.plugins.sourcecontrol.SourceControlService
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name="workspace_project_development_links",indexes=[Index(columnList="project_id")])
class ProjectDevelopmentLink(@Id val id: UUID = UUID.randomUUID(), val projectId: UUID = UUID.randomUUID(), val repositoryId: UUID = UUID.randomUUID(),
    val kind: String = "BRANCH", @Column(length=200) val reference: String = "", val boardId: UUID? = null, val cardId: UUID? = null,
    @Column(length=300) var title: String = "", @Column(length=2000) var url: String = "", var state: String = "", @Column(length=120) var environment: String = "",
    @Column(length=200) var ref: String = "", var manual: Boolean = false, @get:JsonIgnore var configurationHash: String = "", var checkedAt: Instant = Instant.now(), @Version var revision: Long = 0)
interface ProjectDevelopmentLinkRepository : JpaRepository<ProjectDevelopmentLink,UUID> {fun findByProjectId(projectId: UUID): List<ProjectDevelopmentLink>}
data class ProjectDevelopmentInput(val repositoryId: UUID,val kind: String,val reference: String,val boardId: UUID? = null,val cardId: UUID? = null,
    val manualUrl: String? = null,val environment: String = "",val state: String = "unknown")
data class ProjectDeploymentStatusInput(val revision: Long,val state: String)
data class ProjectDevelopmentView(val id: UUID,val repositoryId: UUID,val kind: String,val reference: String?,val title: String,val url: String?,val state: String?,
    val environment: String?,val ref: String?,val manual: Boolean,val available: Boolean,val checkedAt: Instant?,val revision: Long,val boardId: UUID?,val cardId: UUID?,val boardName: String?)

@Service
class ProjectDevelopmentService(private val rows: ProjectDevelopmentLinkRepository,private val repositories: ProjectRepositoryLinkRepository,
    private val settings: ProjectDevelopmentSettingsService,private val access: ProjectAccess,private val source: SourceControlService,
    private val boards: KanbanRepository,private val boardAccess: ProjectBoardAccess,private val plugins: PluginRegistry,private val mapper: ObjectMapper,private val jdbc: JdbcTemplate) {
    fun list(email: String,projectId: UUID): List<ProjectDevelopmentView> {
        val actor=access.actor(email);access.require(projectId,actor)
        val features=settings.effective(projectId)
        val repos=repositories.findByProjectId(projectId).associateBy {it.id}
        val result=rows.findByProjectId(projectId).filter {features.allows(it.kind)}.mapNotNull {row ->
            val board=row.boardId?.let {visibleBoard(actor,projectId,it)}
            if(row.boardId!=null && (board==null || row.cardId!=null && mapper.readValue(board.contentJson,KanbanContent::class.java).cards.none {it.id==row.cardId}))return@mapNotNull null
            val repo=repos[row.repositoryId]
            val available=repo!=null && runCatching {source.approvedFor(repo.providerId,repo.repository,actor.role);source.fingerprint(repo.providerId,actor.role)==row.configurationHash}.getOrDefault(false)
            view(row,available,board?.name)
        }
        access.require(projectId,access.actor(email));return result
    }
    @Transactional fun add(email: String,projectId: UUID,input: ProjectDevelopmentInput): ProjectDevelopmentView {
        val actor=access.actor(email);access.require(projectId,actor,true)
        requireEnabled(projectId,input.kind)
        val repo=repository(projectId,input.repositoryId)
        val board=input.boardId?.let {visibleBoard(actor,projectId,it) ?: throw NotFoundException("Board unavailable")}
        if(input.cardId!=null && (board==null || mapper.readValue(board.contentJson,KanbanContent::class.java).cards.none {it.id==input.cardId}))throw NotFoundException("Ticket unavailable")
        val fingerprint=source.fingerprint(repo.providerId,actor.role)
        val remote=source.trackedResource(repo.providerId,repo.repository,input.kind,input.reference,actor.role,input.manualUrl,input.environment,input.state)
        lock(projectId);recheck(email,projectId,repo,fingerprint,input.kind)
        if(input.boardId!=null && visibleBoard(access.actor(email),projectId,input.boardId)==null)throw NotFoundException("Board unavailable")
        val existing=rows.findByProjectId(projectId)
        if(existing.size>=200)throw BadRequestException("A project supports up to 200 development links")
        if(existing.any {it.repositoryId==repo.id && it.kind==input.kind && it.reference==input.reference && it.boardId==input.boardId && it.cardId==input.cardId})throw ConflictException("This development resource is already linked")
        val row=ProjectDevelopmentLink(projectId=projectId,repositoryId=repo.id,kind=input.kind,reference=input.reference,boardId=input.boardId,cardId=input.cardId,
            title=remote.title,url=remote.url,state=remote.state,environment=remote.environment,ref=remote.ref,manual=remote.manual,configurationHash=fingerprint)
        return view(rows.saveAndFlush(row),true,board?.name)
    }
    @Transactional fun refresh(email: String,projectId: UUID,id: UUID,revision: Long): ProjectDevelopmentView {
        val actor=access.actor(email);access.require(projectId,actor,true)
        val row=record(projectId,id,revision);requireEnabled(projectId,row.kind)
        val board=row.boardId?.let {visibleBoard(actor,projectId,it) ?: throw NotFoundException("Board unavailable")}
        val repo=repository(projectId,row.repositoryId);val fingerprint=source.fingerprint(repo.providerId,actor.role)
        val remote=source.trackedResource(repo.providerId,repo.repository,row.kind,row.reference,actor.role,row.url,row.environment,row.state)
        lock(projectId);recheck(email,projectId,repo,fingerprint,row.kind)
        if(row.boardId!=null && visibleBoard(access.actor(email),projectId,row.boardId)==null)throw NotFoundException("Board unavailable")
        row.title=remote.title;row.url=remote.url;row.state=remote.state;row.environment=remote.environment;row.ref=remote.ref;row.manual=remote.manual;row.configurationHash=fingerprint;row.checkedAt=Instant.now()
        return view(rows.saveAndFlush(row),true,board?.name)
    }
    @Transactional fun remove(email: String,projectId: UUID,id: UUID,revision: Long) {
        access.require(projectId,access.actor(email),true);val row=record(projectId,id,revision)
        row.boardId?.let {visibleBoard(access.actor(email),projectId,it) ?: throw NotFoundException("Board unavailable")}
        rows.delete(row);rows.flush()
    }
    @Transactional fun status(email: String,projectId: UUID,id: UUID,input: ProjectDeploymentStatusInput): ProjectDevelopmentView {
        val actor=access.actor(email);access.require(projectId,actor,true);requireEnabled(projectId,"DEPLOYMENT")
        val row=record(projectId,id,input.revision)
        if(!row.manual || row.kind!="DEPLOYMENT" || input.state !in setOf("unknown","pending","running","success","failed","cancelled"))throw BadRequestException("Choose a supported status for a manually tracked deployment")
        val board=row.boardId?.let {visibleBoard(actor,projectId,it) ?: throw NotFoundException("Board unavailable")}
        val repo=repository(projectId,row.repositoryId);source.approvedFor(repo.providerId,repo.repository,actor.role)
        if(source.fingerprint(repo.providerId,actor.role)!=row.configurationHash)throw ConflictException("Provider settings changed. Refresh this deployment link first.")
        row.state=input.state;row.checkedAt=Instant.now();return view(rows.saveAndFlush(row),true,board?.name)
    }
    private fun repository(projectId: UUID,id: UUID)=repositories.findById(id).orElse(null)?.takeIf {it.projectId==projectId} ?: throw NotFoundException("Project repository unavailable")
    private fun record(projectId: UUID,id: UUID,revision: Long): ProjectDevelopmentLink {
        val row=rows.findById(id).orElse(null)?.takeIf {it.projectId==projectId} ?: throw NotFoundException("Development link unavailable")
        if(row.revision!=revision)throw ConflictException("This development link changed. Reload before editing.")
        return row
    }
    private fun visibleBoard(actor: User,projectId: UUID,id: UUID): KanbanBoard? {
        if(runCatching {plugins.requireTable("kanban","plugin_kanban_boards",actor.role)}.isFailure)return null
        return boards.findById(id).orElse(null)?.takeIf {it.projectId==projectId && boardAccess.readable(actor,it)}
    }
    private fun lock(projectId: UUID)=jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"project-development:$projectId")
    private fun requireEnabled(projectId: UUID,kind: String) {if(!settings.effective(projectId).allows(kind))throw ForbiddenException("Enable this development feature in the project settings first")}
    private fun recheck(email: String,projectId: UUID,repo: ProjectRepositoryLink,fingerprint: String,kind: String) {
        val actor=access.actor(email);access.require(projectId,actor,true);requireEnabled(projectId,kind);repository(projectId,repo.id)
        if(source.fingerprint(repo.providerId,actor.role)!=fingerprint)throw ConflictException("Provider configuration changed. Refresh again.")
    }
    private fun view(row: ProjectDevelopmentLink,available: Boolean,boardName: String?)=ProjectDevelopmentView(row.id,row.repositoryId,row.kind,row.reference.takeIf {available},
        if(available)row.title else "Unavailable development resource",row.url.takeIf {available},row.state.takeIf {available},row.environment.takeIf {available},row.ref.takeIf {available},row.manual,available,row.checkedAt.takeIf {available},row.revision,row.boardId,row.cardId,boardName)
}

@RestController @RequestMapping("/api/projects/{projectId}/development/links")
class ProjectDevelopmentController(private val service: ProjectDevelopmentService,private val rates: com.starter.security.RateLimits) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID)=service.list(user.username,projectId)
    @PostMapping fun add(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@RequestBody input: ProjectDevelopmentInput): ProjectDevelopmentView {rates.check("project-development",user.username,30,60);return service.add(user.username,projectId,input)}
    @PostMapping("/{id}/refresh") fun refresh(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestParam revision: Long): ProjectDevelopmentView {rates.check("project-development",user.username,30,60);return service.refresh(user.username,projectId,id,revision)}
    @PutMapping("/{id}/status") fun status(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestBody input: ProjectDeploymentStatusInput)=service.status(user.username,projectId,id,input)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestParam revision: Long)=service.remove(user.username,projectId,id,revision)
}
