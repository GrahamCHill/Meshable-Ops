package com.starter.plugins.kanban

import java.util.UUID

/** Mention identity survives duplicate display names and later renames. Rendering never treats comment text as HTML. */
object KanbanMentionText {
    private val token = Regex("@\\[([^]\\r\\n]{1,160})]\\(user:([0-9a-fA-F-]{36})\\)")
    fun ids(text: String) = token.findAll(text).mapNotNull { runCatching { UUID.fromString(it.groupValues[2]) }.getOrNull() }.toSet()
    fun plain(text: String) = token.replace(text) { "@${it.groupValues[1]}" }
}
