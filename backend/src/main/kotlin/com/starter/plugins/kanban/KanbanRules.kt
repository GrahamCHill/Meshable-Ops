package com.starter.plugins.kanban

import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import java.util.UUID

/** Apply policies to the saved ticket and saved columns; changing an assignee/policy in the same request cannot grant a move. */
object KanbanRules {
    fun allows(permission: ColumnPermission, actor: UUID, card: KanbanCard, manager: Boolean): Boolean = manager || when (permission.mode) {
        MovePermission.EDITORS -> true
        MovePermission.ASSIGNEE -> card.assigneeId == actor
        MovePermission.REVIEWERS -> actor in card.reviewerIds
        MovePermission.SELECTED_MEMBERS -> actor in permission.userIds
        MovePermission.MANAGERS -> false
    }
    fun canMove(actor: UUID, card: KanbanCard, from: KanbanColumn, to: KanbanColumn, manager: Boolean) =
        allows(from.leave, actor, card, manager) && allows(to.enter, actor, card, manager)
    fun check(actor: UUID, previous: KanbanContent, next: KanbanContent, manager: Boolean) {
        val before = previous.columns.associateBy { it.id }
        val after = next.columns.associateBy { it.id }
        if (!manager && (previous.columns != next.columns || previous.sprints != next.sprints || previous.epics != next.epics))
            throw ForbiddenException("Only a board manager can change columns, permissions, sprints or epics")
        val incoming = next.cards.associateBy { it.id }
        val saved = previous.cards.associateBy { it.id }
        previous.cards.forEach { old ->
            val updated = incoming[old.id]
            val source = before[old.columnId] ?: throw BadRequestException("Saved column unavailable")
            if (updated == null) {
                if (!allows(source.leave, actor, old, manager)) throw ForbiddenException("You cannot remove a ticket from ${source.title}")
            } else if (updated.columnId != old.columnId || updated.status != old.status) {
                val destination = before[updated.columnId] ?: after[updated.columnId]?.takeIf { manager }
                    ?: throw BadRequestException("Destination column unavailable")
                if (!canMove(actor, old, source, destination, manager)) throw ForbiddenException("You cannot move this ticket from ${source.title} to ${destination.title}")
            }
        }
        next.cards.filter { it.id !in saved }.forEach { card ->
            val column = before[card.columnId] ?: after[card.columnId] ?: throw BadRequestException("Destination column unavailable")
            if (!allows(column.enter, actor, card, manager)) throw ForbiddenException("You cannot create a ticket in ${column.title}")
        }
    }
    fun validate(content: KanbanContent) {
        val epics = content.epics
        if (epics.size > 100 || epics.map { it.id }.distinct().size != epics.size || epics.any { it.name.isBlank() || it.name.length > 100 || it.description.length > 2000 ||
            !it.color.matches(Regex("#[0-9a-fA-F]{6}")) }) throw BadRequestException("Use up to 100 named epics with a six-digit color")
        val cards = content.cards.associateBy { it.id }
        content.cards.forEach { card ->
            if (card.tags.size > 20 || card.tags.distinct().size != card.tags.size || card.tags.any { it.isBlank() || it.length > 40 || it.any(Char::isISOControl) } ||
                card.epicId != null && epics.none { it.id == card.epicId }) throw BadRequestException("Use up to 20 unique tags and an existing epic")
            if (card.parentId != null && (card.parentId == card.id || card.parentId !in cards || card.type != TicketType.SUBTASK || cards[card.parentId]?.parentId != null))
                throw BadRequestException("Subtasks must link to an existing top-level ticket on this board")
            if (card.type == TicketType.SUBTASK && card.parentId == null) throw BadRequestException("Choose a parent ticket for a subtask")
        }
        content.columns.flatMap { listOf(it.enter, it.leave) }.forEach { rule ->
            if (rule.userIds.size > 100 || rule.mode != MovePermission.SELECTED_MEMBERS && rule.userIds.isNotEmpty())
                throw BadRequestException("Choose up to 100 members for a selected-member movement rule")
        }
    }
}
