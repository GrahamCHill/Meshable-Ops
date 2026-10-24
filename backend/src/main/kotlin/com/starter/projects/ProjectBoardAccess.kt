package com.starter.projects

import com.starter.access.Roles
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.kanban.KanbanBoard
import com.starter.plugins.kanban.KanbanSummary
import com.starter.repository.postgres.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProjectBoardAccess(private val projects: ProjectAccess, private val users: UserRepository, private val jdbc: JdbcTemplate,
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper, private val groups: WorkGroupRepository) {
    fun teams(row: KanbanBoard): Set<UUID> = mapper.readValue(row.teamIdsJson, object : com.fasterxml.jackson.core.type.TypeReference<Set<UUID>>() {})
    fun readable(actor: User, row: KanbanBoard): Boolean = projects.hasFeature(actor, "plugin.kanban") &&
        (row.projectId?.let { runCatching {
            val project = projects.require(it, actor)
            teams(row).let { ids -> ids.isEmpty() || Roles.admin(actor.role) || project.leadId == actor.id || projects.role(actor, project) == ProjectRole.MANAGER || groups.findAllById(ids).any { it.kind == GroupKind.TEAM && actor.id in projects.groupDetails(it).memberIds } }
        }.getOrDefault(false) } ?: (row.ownerId == actor.id || Roles.admin(actor.role)))
    fun require(actor: User, row: KanbanBoard) { if (!readable(actor, row)) throw NotFoundException("Board unavailable") }
    fun requireManager(actor: User, row: KanbanBoard) { require(actor, row); row.projectId?.let { projects.require(it, actor, true) } }
    fun canManage(actor: User, row: KanbanBoard): Boolean = readable(actor, row) && (Roles.admin(actor.role) ||
        (row.projectId?.let { projects.role(actor, projects.require(it, actor)) in setOf(ProjectRole.MANAGER, ProjectRole.TEAM_LEAD) } ?: (row.ownerId == actor.id)))
    fun attach(actor: User, row: KanbanBoard, projectId: UUID?) {
        if (row.projectId == projectId) return
        if (row.projectId == null && row.ownerId != actor.id && !Roles.admin(actor.role)) throw ForbiddenException("Only the board owner can share it with a project")
        row.projectId?.let { projects.require(it, actor, true) }
        projectId?.let { projects.require(it, actor, true) }
    }
    fun validateTeams(actor: User, row: KanbanBoard, projectId: UUID?, teams: Set<UUID>) {
        if (teams.size > 30) throw BadRequestException("Choose up to 30 board teams")
        if (teams != this.teams(row)) requireManager(actor, row)
        if (projectId == null && teams.isNotEmpty()) throw BadRequestException("Team restrictions require a linked project")
        projectId?.let { id -> if (!projects.details(projects.require(id, actor)).groupIds.containsAll(teams)) throw BadRequestException("Board teams must be explicit teams linked to this project") }
    }
    fun list(actor: User, projectId: UUID? = null): List<KanbanSummary> {
        projectId?.let { projects.require(it, actor) }
        return jdbc.query("""SELECT b.id, b.owner_id, b.name, b.updated_at, b.project_id,
            jsonb_array_length(COALESCE(CAST(b.content_json AS jsonb)->'cards','[]'::jsonb)) AS ticket_count,
            (SELECT count(*) FROM jsonb_array_elements(COALESCE(CAST(b.content_json AS jsonb)->'cards','[]'::jsonb)) card WHERE card->>'status' = 'DONE') AS completed_count
            FROM plugin_kanban_boards b WHERE ${ProjectAccessSql.boardPredicate()} ${if (projectId == null) "" else "AND b.project_id = ?"} ORDER BY b.updated_at DESC, b.id LIMIT 200""",
            { rs, _ -> KanbanSummary(rs.getObject("id", UUID::class.java), rs.getObject("owner_id", UUID::class.java), rs.getString("name"), rs.getTimestamp("updated_at").toInstant(), rs.getObject("project_id", UUID::class.java), rs.getInt("ticket_count"), rs.getInt("completed_count")) }, *(sqlArgs(actor).toList() + listOfNotNull(projectId)).toTypedArray())
    }
    fun sqlArgs(actor: User) = ProjectAccessSql.boardArgs(actor, projects.hasFeature(actor, "projects"))
    fun members(actor: User, row: KanbanBoard): List<ProjectPerson> {
        require(actor, row)
        val ids = row.projectId?.let { projects.members(projects.require(it, actor)) } ?: setOf(row.ownerId)
        return users.findAllById(ids).filter { it.active && it.role != "EXTERNAL" && readable(it, row) }.map { ProjectPerson(it.id, it.name ?: it.username, it.email) }
    }
    fun validatePeople(actor: User, row: KanbanBoard, ids: Set<UUID>) {
        if (ids.isEmpty()) return
        if (!members(actor, row).map { it.id }.toSet().containsAll(ids)) throw BadRequestException("Assignees, reviewers, and mentions must be active internal members who can access this board")
    }
}
