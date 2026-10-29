package com.starter.plugins.workflows

import com.starter.plugins.kanban.*
import org.springframework.stereotype.Component
import java.util.UUID

data class WorkflowTriggerContext(val selectors: Set<String> = emptySet(), val values: Map<String,String> = emptyMap())
interface WorkflowActionContextProvider {
    val actions: List<WorkflowActionDescriptor> get() = emptyList()
    fun actionsFor(method: String, path: String): List<WorkflowActionDescriptor> = if (supports(path)) actions else emptyList()
    fun supports(path: String): Boolean
    fun context(email: String, path: String, body: Any?): WorkflowTriggerContext
}

/** Compare saved board state, so bulk edits cannot bypass a transition/creation/deletion binding. */
@Component
class KanbanWorkflowContext(private val boards: KanbanService) : WorkflowActionContextProvider {
    override val actions = listOf("transition" to "Ticket column transitions", "ticket.create" to "Ticket creation",
        "ticket.delete" to "Ticket deletion", "sprint.start" to "Start sprint", "sprint.complete" to "Complete sprint").map { (key,label) ->
        WorkflowActionDescriptor("kanban.$key",label,"kanban",fields=listOf("trigger.kanban.boardId","trigger.kanban.targetColumnIds","trigger.kanban.projectId"))
    }
    override fun supports(path: String) = path == "/api/plugins/kanban/boards" || path.startsWith("/api/plugins/kanban/boards/")
    override fun context(email: String, path: String, body: Any?): WorkflowTriggerContext {
        if (path == "/api/plugins/kanban/boards" && body is KanbanRequest) return WorkflowTriggerContext(
            if(body.content.cards.isEmpty()) emptySet() else setOf("kanban.ticket.create"),
            mapOf("trigger.kanban.projectId" to body.projectId?.toString().orEmpty()))
        val parts = path.removePrefix("/api/plugins/kanban/boards/").split('/')
        val id = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return WorkflowTriggerContext()
        val board = boards.get(email, id)
        val revision=when(body) { is KanbanRequest -> body.revision;is MoveTicketRequest -> body.revision;is SprintActionRequest -> body.revision;is DeleteTicketRequest -> body.revision;else -> board.revision }
        if(revision != board.revision) throw com.starter.exception.ConflictException("This board changed. Reload before starting its workflow.")
        val values = mutableMapOf("trigger.kanban.boardId" to id.toString(), "trigger.kanban.boardName" to board.name,
            "trigger.kanban.projectId" to board.projectId?.toString().orEmpty(), "trigger.kanban.revision" to board.revision.toString())
        val selectors = mutableSetOf<String>()
        if (body is KanbanRequest) {
            val previous = board.content.cards.associateBy { it.id }
            val next = body.content.cards.associateBy { it.id }
            val moved = body.content.cards.filter { previous[it.id]?.let { old -> old.columnId != it.columnId } == true }
            if (moved.isNotEmpty()) { selectors += "kanban.transition"; values["trigger.kanban.targetColumnIds"] = moved.map { it.columnId }.distinct().joinToString(",") }
            if (next.keys.any { it !in previous }) selectors += "kanban.ticket.create"
            if (previous.keys.any { it !in next }) selectors += "kanban.ticket.delete"
            val oldSprints = board.content.sprints.associateBy { it.id }
            body.content.sprints.forEach { sprint -> if (oldSprints[sprint.id]?.status != sprint.status) when (sprint.status) {
                SprintStatus.ACTIVE -> selectors += "kanban.sprint.start"; SprintStatus.COMPLETED -> selectors += "kanban.sprint.complete"; else -> Unit
            } }
        }
        if (body is MoveTicketRequest) {
            val card = board.content.cards.find { it.id.toString() == parts.getOrNull(2) }
            if (card != null && card.columnId != body.columnId) selectors += "kanban.transition"
            values["trigger.kanban.targetColumnIds"] = body.columnId.toString()
            values["trigger.kanban.ticketId"] = card?.id?.toString().orEmpty()
            values["trigger.kanban.ticketTitle"] = card?.title.orEmpty()
            values["trigger.kanban.storyPoints"] = card?.storyPoints?.toString() ?: "0"
        }
        if (path.endsWith("/start")) selectors += "kanban.sprint.start"
        if (path.endsWith("/complete")) selectors += "kanban.sprint.complete"
        if (path.contains("/cards/") && path.endsWith("/delete")) selectors += "kanban.ticket.delete"
        return WorkflowTriggerContext(selectors, values)
    }
}
