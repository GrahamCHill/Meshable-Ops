package com.starter.plugins.kanban

import com.starter.exception.BadRequestException
import java.util.UUID

/** Sprint planning and completion use the same rules for API actions and full-board writes. */
object KanbanSprintRules {
    fun validate(content: KanbanContent) {
        if (content.sprints.count { it.status == SprintStatus.ACTIVE } > 1)
            throw BadRequestException("Complete the active sprint before starting another")
        if (content.sprints.any { it.goal.length > 500 })
            throw BadRequestException("Sprint goals are limited to 500 characters")
    }

    fun check(previous: KanbanContent, next: KanbanContent) {
        val saved = previous.sprints.associateBy { it.id }
        val incoming = next.sprints.associateBy { it.id }
        if (previous.sprints.any { it.status != SprintStatus.PLANNED && it.id !in incoming })
            throw BadRequestException("Active and completed sprints must be retained")
        next.sprints.forEach { sprint ->
            val before = saved[sprint.id]?.status
            when {
                before == null && sprint.status != SprintStatus.PLANNED ->
                    throw BadRequestException("Create a planned sprint before starting it")
                before == SprintStatus.COMPLETED && sprint.status != before ->
                    throw BadRequestException("Completed sprints cannot be restarted")
                before == SprintStatus.PLANNED && sprint.status == SprintStatus.COMPLETED ->
                    throw BadRequestException("Start the sprint before completing it")
                before == SprintStatus.ACTIVE && sprint.status == SprintStatus.PLANNED ->
                    throw BadRequestException("An active sprint must be completed")
            }
            if (before != sprint.status && sprint.status == SprintStatus.ACTIVE && next.cards.none { it.sprintId == sprint.id && it.parentId == null })
                throw BadRequestException("Add at least one ticket before starting the sprint")
            if (before == SprintStatus.ACTIVE && sprint.status == SprintStatus.COMPLETED && next.cards.any { it.sprintId == sprint.id && it.status != CardStatus.DONE })
                throw BadRequestException("Move unfinished tickets to the backlog or a planned sprint before completing")
        }
        val savedCards = previous.cards.associateBy { it.id }
        if (next.cards.any { incoming[it.sprintId]?.status == SprintStatus.COMPLETED && savedCards[it.id]?.sprintId != it.sprintId })
            throw BadRequestException("Tickets cannot be added to a completed sprint")
    }

    fun start(content: KanbanContent, id: UUID): KanbanContent {
        val sprint = content.sprints.find { it.id == id } ?: throw BadRequestException("Sprint unavailable")
        if (sprint.status != SprintStatus.PLANNED) throw BadRequestException("Only a planned sprint can be started")
        return content.copy(sprints = content.sprints.map { if (it.id == id) it.copy(status = SprintStatus.ACTIVE) else it })
    }

    fun complete(content: KanbanContent, id: UUID, destination: UUID?): KanbanContent {
        val sprint = content.sprints.find { it.id == id } ?: throw BadRequestException("Sprint unavailable")
        if (sprint.status != SprintStatus.ACTIVE) throw BadRequestException("Only the active sprint can be completed")
        if (destination != null && content.sprints.none { it.id == destination && it.status == SprintStatus.PLANNED })
            throw BadRequestException("Choose the backlog or a planned sprint for unfinished work")
        val unfinished = content.cards.filter { it.sprintId == id && it.parentId == null && it.status != CardStatus.DONE }.map { it.id }.toSet()
        if (content.cards.any { it.sprintId == id && it.parentId != null && it.parentId !in unfinished && it.status != CardStatus.DONE })
            throw BadRequestException("Complete unfinished subtasks on done tickets before completing the sprint")
        return content.copy(
            sprints = content.sprints.map { if (it.id == id) it.copy(status = SprintStatus.COMPLETED) else it },
            cards = content.cards.map { card -> if (card.id in unfinished || card.parentId in unfinished) card.copy(sprintId = destination) else card },
        )
    }
}
