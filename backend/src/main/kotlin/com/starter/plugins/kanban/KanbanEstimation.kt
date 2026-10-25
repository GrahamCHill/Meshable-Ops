package com.starter.plugins.kanban

import com.starter.plugins.*
import com.starter.projects.ProjectAccess
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.access.Roles
import com.starter.access.AccessControlService
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

data class KanbanEstimation(val storyPoints: Boolean = true, val tshirtSizes: Boolean = false)
@Entity @Table(name = "plugin_kanban_estimation")
class ProjectEstimation(@Id val projectId: UUID = UUID.randomUUID(), var inherit: Boolean = true,
    var storyPoints: Boolean = true, var tshirtSizes: Boolean = false, @Version var revision: Long = 0)
interface ProjectEstimationRepository : JpaRepository<ProjectEstimation, UUID>
data class ProjectEstimationInput(val inherit: Boolean = true, val storyPoints: Boolean = true, val tshirtSizes: Boolean = false, val revision: Long = -1)
data class ProjectEstimationView(val inherit: Boolean, val storyPoints: Boolean, val tshirtSizes: Boolean, val revision: Long, val canManage: Boolean)

@Service
class KanbanEstimationService(private val configuration: PluginConfigurationService, private val records: ProjectEstimationRepository,
    private val projects: ProjectAccess, private val registry: PluginRegistry, private val jdbc: JdbcTemplate, private val access: AccessControlService) {
    fun effective(projectId: UUID?): KanbanEstimation {
        val own = projectId?.let { records.findById(it).orElse(null) }
        if (own != null && !own.inherit) return KanbanEstimation(own.storyPoints, own.tshirtSizes)
        val values = configuration.current("kanban")
        return KanbanEstimation(values["storyPoints"] != "false", values["tshirtSizes"] == "true")
    }
    fun view(email: String, projectId: UUID): ProjectEstimationView {
        val actor = projects.actor(email)
        if (!Roles.admin(actor.role)) throw ForbiddenException("Only administrators can manage project estimation settings")
        access.requireFeature("admin.plugins")
        projects.require(projectId, actor)
        registry.requireTable("kanban", "plugin_kanban_estimation", actor.role)
        val record = records.findById(projectId).orElse(null); val current = effective(projectId)
        return ProjectEstimationView(record?.inherit ?: true, current.storyPoints, current.tshirtSizes, record?.revision ?: -1, true)
    }
    @Transactional
    fun save(email: String, projectId: UUID, input: ProjectEstimationInput): ProjectEstimationView {
        val actor = projects.actor(email)
        if (!Roles.admin(actor.role)) throw ForbiddenException("Only administrators can manage project estimation settings")
        access.requireFeature("admin.plugins")
        projects.require(projectId, actor, true)
        registry.requireTable("kanban", "plugin_kanban_estimation", actor.role, true)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", org.springframework.jdbc.core.RowCallbackHandler {}, "kanban-estimation:$projectId")
        val row = records.findById(projectId).orElse(null)
        if ((row?.revision ?: -1) != input.revision) throw ConflictException("Estimation settings changed. Reload before saving.")
        val next = row ?: ProjectEstimation(projectId)
        next.inherit = input.inherit; next.storyPoints = input.storyPoints; next.tshirtSizes = input.tshirtSizes
        records.saveAndFlush(next)
        return view(email, projectId)
    }
}
@RestController @RequestMapping("/api/plugins/kanban/projects/{projectId}/estimation")
class KanbanEstimationController(private val service: KanbanEstimationService) {
    @GetMapping fun view(@AuthenticationPrincipal user: UserDetails, @PathVariable projectId: UUID) = service.view(user.username, projectId)
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails, @PathVariable projectId: UUID, @RequestBody input: ProjectEstimationInput) = service.save(user.username, projectId, input)
}
