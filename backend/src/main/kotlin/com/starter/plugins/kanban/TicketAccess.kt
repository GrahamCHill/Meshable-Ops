package com.starter.plugins.kanban

import com.starter.access.Roles
import com.starter.exception.*
import com.starter.model.postgres.User
import java.util.UUID

object TicketAccess {
    fun create(actor: User, policy: TicketAccessPolicy) = when(policy.createMode) {
        TicketCreationMode.ANY_MEMBER -> true
        TicketCreationMode.ADMINS -> Roles.admin(actor.role)
        TicketCreationMode.SELECTED_MEMBERS -> actor.id in policy.creatorIds
    }
    fun delete(actor: User, policy: TicketAccessPolicy) = Roles.admin(actor.role) &&
        (policy.deleteMode == TicketDeletionMode.ALL_ADMINS || actor.id in policy.deleterIds)
    fun check(actor: User, previous: KanbanContent, next: KanbanContent, confirmed: Set<UUID>) {
        if (previous.ticketAccess != next.ticketAccess && !Roles.admin(actor.role)) throw ForbiddenException("Only administrators can change ticket creation and deletion permissions")
        if (next.ticketAccess.creatorIds.size > 500 || next.ticketAccess.deleterIds.size > 500) throw BadRequestException("Choose at most 500 members per ticket permission")
        val old = previous.cards.map {it.id}.toSet(); val ids = next.cards.map {it.id}.toSet()
        if ((ids - old).isNotEmpty() && !create(actor, previous.ticketAccess)) throw ForbiddenException("You cannot create tickets on this board")
        val removed = old - ids
        if (removed.isNotEmpty()) {
            if (!delete(actor, previous.ticketAccess)) throw ForbiddenException("Only the permitted administrators can delete tickets")
            if (removed != confirmed) throw BadRequestException("Confirm every ticket being deleted, including subtasks")
        }
    }
}
