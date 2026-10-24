package com.starter.plugins.kanban

/** Explicit column mappings are authoritative; legacy Done columns acquire their missing mapping on save. */
object KanbanCompletion {
    fun status(column: KanbanColumn): CardStatus? = column.status ?: when(column.title.trim().lowercase()) {
        "done", "complete", "completed" -> CardStatus.DONE
        else -> null
    }
    fun read(value: KanbanContent): KanbanContent {
        val columns=value.columns.map {it.copy(status=status(it))}
        val states=columns.associate {it.id to it.status}
        return value.copy(columns=columns,cards=value.cards.map {card->card.copy(status=states[card.columnId] ?: card.status)})
    }
}
