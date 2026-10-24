package com.starter.projects

import com.starter.exception.ConflictException
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class DevelopmentFeatures(val repositories: Boolean = true, val branches: Boolean = false, val pullRequests: Boolean = true, val deployments: Boolean = false) {
    fun allows(kind: String) = repositories && when(kind) { "BRANCH" -> branches; "PULL_REQUEST" -> pullRequests; "DEPLOYMENT" -> deployments; "REPOSITORY" -> true; else -> false }
}
@Entity @Table(name="workspace_project_development_settings")
class ProjectDevelopmentSettingsRecord(@Id val projectId: UUID = UUID.randomUUID(), var repositories: Boolean = true,
    var branches: Boolean = false, var pullRequests: Boolean = true, var deployments: Boolean = false, @Version var revision: Long = 0)
interface ProjectDevelopmentSettingsRepository : JpaRepository<ProjectDevelopmentSettingsRecord, UUID>
data class DevelopmentSettingsInput(val features: DevelopmentFeatures, val revision: Long = -1)
data class DevelopmentSettingsView(val features: DevelopmentFeatures, val revision: Long, val canManage: Boolean)

@Service
class ProjectDevelopmentSettingsService(private val rows: ProjectDevelopmentSettingsRepository, private val access: ProjectAccess, private val jdbc: JdbcTemplate) {
    fun effective(projectId: UUID): DevelopmentFeatures = rows.findById(projectId).orElse(null)?.let { DevelopmentFeatures(it.repositories,it.branches,it.pullRequests,it.deployments) } ?: DevelopmentFeatures()
    fun view(email: String, projectId: UUID): DevelopmentSettingsView {
        val actor=access.actor(email); val project=access.require(projectId,actor)
        val row=rows.findById(projectId).orElse(null)
        return DevelopmentSettingsView(effective(projectId),row?.revision ?: -1,access.role(actor,project)!=ProjectRole.MEMBER)
    }
    @Transactional fun save(email: String, projectId: UUID, input: DevelopmentSettingsInput): DevelopmentSettingsView {
        access.require(projectId,access.actor(email),true)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"project-development:$projectId")
        val row=rows.findById(projectId).orElse(null)
        if((row?.revision ?: -1)!=input.revision)throw ConflictException("Development settings changed. Reload before saving.")
        val next=row ?: ProjectDevelopmentSettingsRecord(projectId)
        next.repositories=input.features.repositories;next.branches=input.features.branches;next.pullRequests=input.features.pullRequests;next.deployments=input.features.deployments
        rows.saveAndFlush(next);return view(email,projectId)
    }
}
@RestController @RequestMapping("/api/projects/{projectId}/development/settings")
class ProjectDevelopmentSettingsController(private val service: ProjectDevelopmentSettingsService) {
    @GetMapping fun view(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID)=service.view(user.username,projectId)
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@RequestBody input: DevelopmentSettingsInput)=service.save(user.username,projectId,input)
}
