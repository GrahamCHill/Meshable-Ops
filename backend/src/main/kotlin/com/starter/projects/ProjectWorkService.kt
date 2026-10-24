package com.starter.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import com.starter.plugins.kanban.*
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class ProjectWorkItem(val boardId: UUID, val boardName: String, val card: KanbanCard, val columnName: String = "", val epicName: String? = null)
data class WorkBreakdown(val id: String, val label: String, val count: Int, val completed: Int = 0)
data class ProjectWorkSummary(val total: Int, val completed: Int, val overdue: Int, val storyPoints: Double,
    val stages: List<WorkBreakdown>, val assignees: List<WorkBreakdown>, val epics: List<WorkBreakdown>, val tags: List<WorkBreakdown>)
data class DeliveryRisk(val userId: UUID, val name: String, val score: Int, val overdue: Int, val blocked: Int, val inProgress: Int, val dueSoon: Int, val explanation: String)

@Service
class ProjectWorkService(private val boards: KanbanRepository, private val mapper: ObjectMapper, private val access: ProjectAccess,
    private val boardAccess: ProjectBoardAccess, private val registry: PluginRegistry, private val users: UserRepository) {
    fun sprints(email: String, projectId: UUID): List<Map<String, Any>> {
        val actor = access.actor(email); access.require(projectId, actor)
        if (!access.hasFeature(actor, "plugin.kanban") || registry.available(actor.role).none { it.manifest.id == "kanban" }) return emptyList()
        registry.requireTable("kanban", "plugin_kanban_boards", actor.role)
        return boards.findByProjectId(projectId).filter { boardAccess.readable(actor, it) }.flatMap { board -> mapper.readValue(board.contentJson, KanbanContent::class.java).sprints.map { mapOf("boardId" to board.id, "boardName" to board.name, "sprint" to it) } }
    }
    fun work(projectId: UUID, actor: User): List<ProjectWorkItem> {
        access.require(projectId, actor)
        if (registry.available(actor.role).none { it.manifest.id == "kanban" } || !access.hasFeature(actor, "plugin.kanban")) return emptyList()
        registry.requireTable("kanban", "plugin_kanban_boards", actor.role)
        val rows = boards.findByProjectId(projectId)
        if (rows.size > 200) throw BadRequestException("A project supports reporting on up to 200 boards")
        return rows.filter { boardAccess.readable(actor, it) }.flatMap { board ->
            val content = mapper.readValue(board.contentJson, KanbanContent::class.java)
            content.cards.map { ProjectWorkItem(board.id, board.name, it, content.columns.find { column -> column.id == it.columnId }?.title ?: it.status.name,
                content.epics.find { epic -> epic.id == it.epicId }?.name) }
        }
    }
    fun summary(email: String, projectId: UUID): ProjectWorkSummary {
        val actor = access.actor(email); val project = access.require(projectId, actor)
        val items = work(projectId, actor)
        val names = users.findAllById(items.mapNotNull { it.card.assigneeId }.toSet()).filter { it.active && access.canRead(it, project) }
            .associate { it.id.toString() to (it.name ?: it.username) }
        return summarize(items, names, LocalDate.now(ZoneOffset.UTC))
    }
    fun risks(email: String, projectId: UUID): List<DeliveryRisk> {
        val actor = access.actor(email); val project = access.require(projectId, actor, true)
        val cards = work(projectId, actor).map { it.card }
        val assignees = cards.mapNotNull { it.assigneeId }.toSet()
        return users.findAllById(access.members(project)).filter { it.id in assignees && it.active && it.role != "EXTERNAL" && access.canRead(it, project) }.map { user ->
            score(user.id, user.name ?: user.username, cards.filter { it.assigneeId == user.id }, LocalDate.now(ZoneOffset.UTC))
        }
    }
    companion object {
        fun summarize(items: List<ProjectWorkItem>, names: Map<String, String>, today: LocalDate): ProjectWorkSummary {
            fun grouped(key: (ProjectWorkItem) -> String, label: (String) -> String) = items.groupBy(key).map { (id, values) ->
                WorkBreakdown(id, label(id), values.size, values.count { it.card.status == CardStatus.DONE })
            }.sortedWith(compareByDescending<WorkBreakdown> { it.count }.thenBy { it.label })
            val epicNames = items.filter { it.card.epicId != null }.associate { "${it.boardId}:${it.card.epicId}" to (it.epicName ?: "Epic") }
            val cards = items.map { it.card }
            return ProjectWorkSummary(cards.size, cards.count { it.status == CardStatus.DONE },
                cards.count { it.status != CardStatus.DONE && it.dueDate?.isBefore(today) == true }, cards.sumOf { it.storyPoints ?: 0.0 },
                grouped({ it.columnName.ifBlank { it.card.status.name } }, { it }),
                grouped({ it.card.assigneeId?.toString() ?: "unassigned" }, { if (it == "unassigned") "Unassigned" else names[it] ?: "Former or unavailable member" }),
                grouped({ if (it.card.epicId == null) "none" else "${it.boardId}:${it.card.epicId}" }, { epicNames[it] ?: "No epic" }),
                items.flatMap { it.card.tags }.groupingBy { it }.eachCount().map { WorkBreakdown(it.key, it.key, it.value) }.sortedByDescending { it.count })
        }
        fun score(id: UUID, name: String, cards: List<KanbanCard>, today: LocalDate): DeliveryRisk {
            val open = cards.filter { it.status != CardStatus.DONE }
            val overdue = open.count { it.dueDate?.isBefore(today) == true }
            val blocked = open.count { it.blocked }
            val progress = open.count { it.status == CardStatus.IN_PROGRESS }
            val soon = open.count { it.status == CardStatus.TODO && it.dueDate?.let { date -> !date.isBefore(today) && !date.isAfter(today.plusDays(3)) } == true }
            val score = (overdue * 20 + blocked * 15 + soon * 10 + (progress - 5).coerceAtLeast(0) * 5).coerceAtMost(100)
            return DeliveryRisk(id, name, score, overdue, blocked, progress, soon,
                "Delivery indicator only: overdue ×20 + blocked ×15 + unstarted due within 3 days ×10 + in-progress tasks above 5 ×5; capped at 100. Uses current project tasks, not personal performance or sensitive attributes.")
        }
    }
}

@RestController @RequestMapping("/api/projects/{id}")
class ProjectWorkController(private val work: ProjectWorkService, private val access: ProjectAccess) {
    @GetMapping("/work") fun work(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: UUID) = work.work(id, access.actor(actor.username))
    @GetMapping("/work-summary") fun summary(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: UUID) = work.summary(actor.username, id)
    @GetMapping("/risks") fun risks(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: UUID) = work.risks(actor.username, id)
    @GetMapping("/sprints") fun sprints(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: UUID) = work.sprints(actor.username, id)
}
