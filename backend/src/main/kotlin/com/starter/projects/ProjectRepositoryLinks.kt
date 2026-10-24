package com.starter.projects

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.exception.*
import com.starter.plugins.sourcecontrol.SourceControlService
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name="workspace_project_repositories",uniqueConstraints=[UniqueConstraint(columnNames=["project_id","provider_id","repository"])])
class ProjectRepositoryLink(@Id val id:UUID=UUID.randomUUID(),val projectId:UUID=UUID.randomUUID(),val providerId:String="",
    var repository:String="",var title:String="",@Column(length=2000) var url:String="",
    @get:JsonIgnore var configurationHash:String="",var checkedAt:Instant=Instant.now(),@Version var revision:Long=0)
interface ProjectRepositoryLinkRepository:JpaRepository<ProjectRepositoryLink,UUID> {fun findByProjectId(projectId:UUID):List<ProjectRepositoryLink>}
data class ProjectRepositoryInput(val providerId:String,val repository:String)
data class ProjectRepositoryView(val id:UUID,val providerId:String,val repository:String?,val title:String,val url:String?,val available:Boolean,val revision:Long,val checkedAt:Instant?)

@Service
class ProjectRepositoryLinks(private val links:ProjectRepositoryLinkRepository,private val access:ProjectAccess,private val source:SourceControlService,private val jdbc:JdbcTemplate) {
    fun list(email:String,projectId:UUID):List<ProjectRepositoryView> {
        val actor=access.actor(email);access.require(projectId,actor)
        val result=links.findByProjectId(projectId).map {row->
            val available=runCatching {source.approvedFor(row.providerId,row.repository,actor.role);source.fingerprint(row.providerId,actor.role)==row.configurationHash}.getOrDefault(false)
            view(row,available)
        }
        access.require(projectId,access.actor(email));return result
    }
    @Transactional fun add(email:String,projectId:UUID,input:ProjectRepositoryInput):ProjectRepositoryView {
        val actor=access.actor(email);access.require(projectId,actor,true)
        val before=source.fingerprint(input.providerId,actor.role)
        val resource=source.resource(input.providerId,input.repository,null,actor.role)
        access.require(projectId,access.actor(email),true)
        if(source.fingerprint(input.providerId,actor.role)!=before)throw ConflictException("Provider configuration changed. Try linking the repository again.")
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"project-repositories:$projectId")
        val name=if(input.providerId=="github")resource.repository.lowercase()else resource.repository
        if(links.findByProjectId(projectId).any {it.providerId==input.providerId&&it.repository==name})throw ConflictException("This repository is already linked to the project")
        return view(links.saveAndFlush(ProjectRepositoryLink(projectId=projectId,providerId=input.providerId,repository=name,title=resource.title,url=resource.url,configurationHash=before)),true)
    }
    @Transactional fun refresh(email:String,projectId:UUID,id:UUID,revision:Long):ProjectRepositoryView {
        val actor=access.actor(email);access.require(projectId,actor,true)
        val row=require(projectId,id,revision);val before=source.fingerprint(row.providerId,actor.role)
        val resource=source.resource(row.providerId,row.repository,null,actor.role)
        access.require(projectId,access.actor(email),true)
        if(source.fingerprint(row.providerId,actor.role)!=before)throw ConflictException("Provider configuration changed. Refresh again.")
        row.title=resource.title;row.url=resource.url;row.configurationHash=before;row.checkedAt=Instant.now()
        return view(links.saveAndFlush(row),true)
    }
    @Transactional fun remove(email:String,projectId:UUID,id:UUID,revision:Long) {
        access.require(projectId,access.actor(email),true);links.delete(require(projectId,id,revision));links.flush()
    }
    private fun require(projectId:UUID,id:UUID,revision:Long):ProjectRepositoryLink {
        val row=links.findById(id).orElse(null)?.takeIf {it.projectId==projectId} ?: throw NotFoundException("Repository link unavailable")
        if(row.revision!=revision)throw ConflictException("This repository link changed. Reload before editing.")
        return row
    }
    private fun view(row:ProjectRepositoryLink,available:Boolean)=ProjectRepositoryView(row.id,row.providerId,row.repository.takeIf {available},if(available)row.title else "Unavailable repository",row.url.takeIf {available},available,row.revision,row.checkedAt.takeIf {available})
}

@RestController @RequestMapping("/api/projects/{projectId}/repositories")
class ProjectRepositoryController(private val service:ProjectRepositoryLinks,private val rates:com.starter.security.RateLimits) {
    @GetMapping fun list(@AuthenticationPrincipal user:UserDetails,@PathVariable projectId:UUID)=service.list(user.username,projectId)
    @PostMapping fun add(@AuthenticationPrincipal user:UserDetails,@PathVariable projectId:UUID,@RequestBody input:ProjectRepositoryInput):ProjectRepositoryView {rates.check("project-git",user.username,30,60);return service.add(user.username,projectId,input)}
    @PostMapping("/{id}/refresh") fun refresh(@AuthenticationPrincipal user:UserDetails,@PathVariable projectId:UUID,@PathVariable id:UUID,@RequestParam revision:Long):ProjectRepositoryView {rates.check("project-git",user.username,30,60);return service.refresh(user.username,projectId,id,revision)}
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal user:UserDetails,@PathVariable projectId:UUID,@PathVariable id:UUID,@RequestParam revision:Long)=service.remove(user.username,projectId,id,revision)
}
