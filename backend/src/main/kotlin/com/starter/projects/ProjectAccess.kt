package com.starter.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.access.AccessControlService
import com.starter.access.Roles
import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.plugins.PluginActor
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProjectAccess(private val projects: ProjectRepository, private val groups: WorkGroupRepository,
    private val actors: PluginActor, private val access: AccessControlService, private val mapper: ObjectMapper) {
    fun actor(email: String): User = actors.current(email).also(::requireFeature)
    fun requireFeature(actor: User) {
        if (!actor.active || actor.role == "EXTERNAL" || "projects" !in access.features(actor)) throw ForbiddenException("Projects are unavailable to your account")
    }
    fun details(project: ProjectRecord) = mapper.readValue(project.detailsJson, ProjectDetails::class.java)
    fun groupDetails(group: WorkGroupRecord) = mapper.readValue(group.detailsJson, WorkGroupData::class.java)
    fun role(actor: User, project: ProjectRecord): ProjectRole? {
        if (!actor.active || actor.role == "EXTERNAL" || "projects" !in access.features(actor)) return null
        if (Roles.admin(actor.role)) return ProjectRole.MANAGER
        if (actor.id == project.leadId) return ProjectRole.TEAM_LEAD
        val value = details(project)
        val direct = value.members.filter { it.userId == actor.id }.map { it.role }
        val shared = groups.findAllById(value.groupIds).filter { it.kind == GroupKind.TEAM && actor.id in groupDetails(it).memberIds }.map { it.projectRole }
        return (direct + shared).maxByOrNull { it.ordinal }
    }
    fun require(id: UUID, actor: User, manage: Boolean = false): ProjectRecord {
        val row = projects.findById(id).orElse(null) ?: throw NotFoundException("Project unavailable")
        val role = role(actor, row) ?: throw NotFoundException("Project unavailable")
        if (manage && role == ProjectRole.MEMBER) throw ForbiddenException("A project manager, team lead, or administrator must make this change")
        return row
    }
    fun canRead(actor: User, project: ProjectRecord) = role(actor, project) != null
    fun members(project: ProjectRecord): Set<UUID> = details(project).let { data ->
        data.members.map { it.userId }.toSet() + setOf(project.leadId) +
            groups.findAllById(data.groupIds).filter { it.kind == GroupKind.TEAM }.flatMap { groupDetails(it).memberIds }
    }
    fun hasFeature(actor: User, feature: String) = actor.active && actor.role != "EXTERNAL" && feature in access.features(actor)
}

/** Predicates apply membership before LIMIT; department/tag membership never grants project access. */
object ProjectAccessSql {
    fun predicate(alias: String = "p") = """(? OR $alias.lead_id = ?
        OR EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(CAST($alias.details_json AS jsonb)->'members', '[]'::jsonb)) member WHERE member->>'userId' = ?)
        OR EXISTS (SELECT 1 FROM workspace_work_groups g WHERE g.kind = 'TEAM'
            AND jsonb_exists(COALESCE(CAST($alias.details_json AS jsonb)->'groupIds', '[]'::jsonb), CAST(g.id AS text))
            AND jsonb_exists(COALESCE(CAST(g.details_json AS jsonb)->'memberIds', '[]'::jsonb), ?)))"""
    fun args(actor: User): Array<Any> = arrayOf(Roles.admin(actor.role), actor.id, actor.id.toString(), actor.id.toString())
    fun boardPredicate(alias: String = "b") = """(($alias.project_id IS NULL AND (? OR $alias.owner_id = ?)) OR (? AND EXISTS (SELECT 1 FROM workspace_projects p WHERE p.id = $alias.project_id AND ${predicate()}
        AND (CAST($alias.team_ids_json AS jsonb) = '[]'::jsonb OR ? OR p.lead_id = ?
            OR EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(CAST(p.details_json AS jsonb)->'members', '[]'::jsonb)) member WHERE member->>'userId' = ? AND member->>'role' = 'MANAGER')
            OR EXISTS (SELECT 1 FROM workspace_work_groups g WHERE g.kind = 'TEAM' AND jsonb_exists(CAST(g.details_json AS jsonb)->'memberIds', ?)
                AND (jsonb_exists(CAST($alias.team_ids_json AS jsonb), CAST(g.id AS text)) OR (g.project_role = 'MANAGER' AND jsonb_exists(CAST(p.details_json AS jsonb)->'groupIds', CAST(g.id AS text)))))))))"""
    fun boardArgs(actor: User, projects: Boolean): Array<Any> = arrayOf(Roles.admin(actor.role), actor.id, projects, *args(actor), Roles.admin(actor.role), actor.id, actor.id.toString(), actor.id.toString())
}
