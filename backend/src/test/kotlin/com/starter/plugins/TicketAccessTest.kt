package com.starter.plugins

import com.starter.plugins.kanban.*
import com.starter.model.postgres.User
import com.starter.exception.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class TicketAccessTest {
    private val member = User()
    private val admin = User(role = "ADMIN")
    private val card = KanbanCard(UUID.randomUUID(), UUID.randomUUID(), "Ticket")
    private val content = KanbanContent(cards = listOf(card))
    @Test fun `board members may create by default but only administrators may delete`() {
        assertThat(TicketAccess.create(member, TicketAccessPolicy())).isTrue()
        assertThat(TicketAccess.delete(member, TicketAccessPolicy(deleterIds = setOf(member.id)))).isFalse()
        assertThatThrownBy { TicketAccess.check(member, content, content.copy(cards = emptyList()), setOf(card.id)) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `full board replacement cannot bypass selected creators or grant its own permission`() {
        val restricted = content.copy(ticketAccess = TicketAccessPolicy(createMode = TicketCreationMode.SELECTED_MEMBERS))
        val added = restricted.copy(cards = restricted.cards + card.copy(id = UUID.randomUUID()))
        assertThatThrownBy { TicketAccess.check(member, restricted, added, emptySet()) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { TicketAccess.check(member, restricted, added.copy(ticketAccess = TicketAccessPolicy()), emptySet()) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `every deletion must be confirmed and selected admin restrictions also apply to admins`() {
        assertThatThrownBy { TicketAccess.check(admin, content, content.copy(cards = emptyList()), emptySet()) }.isInstanceOf(BadRequestException::class.java)
        TicketAccess.check(admin, content, content.copy(cards = emptyList()), setOf(card.id))
        val restricted = TicketAccessPolicy(deleteMode = TicketDeletionMode.SELECTED_ADMINS)
        assertThat(TicketAccess.delete(admin, restricted)).isFalse()
        assertThat(TicketAccess.delete(admin, restricted.copy(deleterIds = setOf(admin.id)))).isTrue()
    }
}
