package com.starter.plugins.kanban

import com.starter.exception.BadRequestException
import java.util.UUID

object KanbanGraphRules {
    fun validate(content: KanbanContent) {
        val columns = content.columns.map { it.id }.toSet()
        val graph = content.workflow
        if (graph.transitions.size > 200 || graph.transitions.map { it.id }.distinct().size != graph.transitions.size ||
            graph.transitions.map { it.source to it.target }.distinct().size != graph.transitions.size ||
            graph.transitions.any { it.source !in columns || it.target !in columns || it.source == it.target || it.name.isBlank() || it.name.length > 80 } ||
            graph.positions.size > 20 || graph.positions.any { (id, pos) -> id !in columns || !pos.x.isFinite() || !pos.y.isFinite() || kotlin.math.abs(pos.x) > 10000 || kotlin.math.abs(pos.y) > 10000 })
            throw BadRequestException("Use up to 200 named transitions between existing columns and valid node positions")
        val ids = content.cards.map { it.id }.toSet()
        val dependencies = mutableMapOf<UUID, MutableSet<UUID>>()
        content.cards.forEach { card ->
            if (card.links.size > 50 || card.links.distinct().size != card.links.size || card.links.any { it.targetId == card.id || it.targetId !in ids })
                throw BadRequestException("Link up to 50 distinct existing tickets on this board, excluding the ticket itself")
            card.links.forEach { link -> when (link.type) {
                TicketLinkType.BLOCKS -> dependencies.getOrPut(card.id) { mutableSetOf() }.add(link.targetId)
                TicketLinkType.BLOCKED_BY -> dependencies.getOrPut(link.targetId) { mutableSetOf() }.add(card.id)
                else -> Unit
            } }
        }
        val visiting = mutableSetOf<UUID>(); val visited = mutableSetOf<UUID>()
        fun visit(id: UUID) {
            if (id in visiting) throw BadRequestException("Blocking relationships cannot form a cycle")
            if (!visited.add(id)) return
            visiting.add(id); dependencies[id].orEmpty().forEach(::visit); visiting.remove(id)
        }
        ids.forEach(::visit)
    }
}
