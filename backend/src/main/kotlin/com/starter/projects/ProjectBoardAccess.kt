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
class ProjectBoardAccess(private val projects: ProjectAccess, private val users: UserRepository, private val jdbc: JdbcTemplate) {
    fun readable(actor: User, row: KanbanBoard): Boolean = projects.hasFeature(actor, "plugin.kanban") &&
        (row.projectId?.let { runCatching { projects.require(it, actor) }.isSuccess } ?: (row.ownerId == actor.id || Roles.admin(actor.role)))
    fun require(actor: User, row: KanbanBoard) { if (!readable(actor, row)) throw NotFoundException("Board unavailable") }
    fun requireManager(actor: User, row: KanbanBoard) { require(actor, row); row.projectId?.let { projects.require(it, actor, true) } }
    fun attach(actor: User, row: KanbanBoard, projectId: UUID?) {
        if (row.projectId == projectId) return
        if (row.projectId == null && row.ownerId != actor.id && !Roles.admin(actor.role)) throw ForbiddenException("Only the board owner can share it with a project")
        row.projectId?.let { projects.require(it, actor, true) }
        projectId?.let { projects.require(it, actor, true) }
    }
    fun list(actor: User): List<KanbanSummary> = jdbc.query("SELECT b.id, b.owner_id, b.name, b.updated_at, b.project_id FROM plugin_kanban_boards b WHERE ${ProjectAccessSql.boardPredicate()} ORDER BY b.updated_at DESC, b.id LIMIT 100",
        { rs, _ -> KanbanSummary(rs.getObject("id", UUID::class.java), rs.getObject("owner_id", UUID::class.java), rs.getString("name"), rs.getTimestamp("updated_at").toInstant(), rs.getObject("project_id", UUID::class.java)) }, *sqlArgs(actor))
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
