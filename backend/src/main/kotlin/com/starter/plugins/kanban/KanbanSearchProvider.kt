package com.starter.plugins.kanban

import com.starter.ai.LocalTextVectors
import com.starter.model.postgres.User
import com.starter.plugins.PluginSearchProvider
import com.starter.service.VectorSearchPoint
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class KanbanSearchProvider(private val jdbc: JdbcTemplate) : PluginSearchProvider {
    override val pluginId = "kanban"
    override val tableName = "plugin_kanban_boards"

    fun index(board: KanbanBoard, content: KanbanContent) {
        val text = (listOf(board.name) + content.columns.map { it.title } + content.cards.flatMap { listOf(it.title, it.description) }).joinToString("\n")
        jdbc.update("UPDATE plugin_kanban_boards SET search_text = ?, search_embedding = CAST(? AS vector) WHERE id = ?",
            text, LocalTextVectors.embed(text).joinToString(",", "[", "]"), board.id)
    }

    override fun search(query: String, limit: Int, actor: User): List<VectorSearchPoint> {
        val vector = LocalTextVectors.embed(query).joinToString(",", "[", "]")
        return jdbc.query("""
            WITH ranked AS (
                SELECT id, name, coalesce(nullif(search_text, ''), name || ' ' || content_json) AS text,
                    greatest(coalesce(1 - (search_embedding <=> CAST(? AS vector)), 0),
                        ts_rank_cd(to_tsvector('simple', coalesce(nullif(search_text, ''), name || ' ' || content_json)), plainto_tsquery('simple', ?))) AS score,
                    to_tsvector('simple', coalesce(nullif(search_text, ''), name || ' ' || content_json)) @@ plainto_tsquery('simple', ?) AS keyword
                FROM plugin_kanban_boards WHERE (? = TRUE OR owner_id = ?)
            ) SELECT id, name, text, score, keyword FROM ranked WHERE keyword OR score >= 0.15 ORDER BY score DESC, id LIMIT ?
        """.trimIndent(), { rs, _ ->
            val id = rs.getString("id")
            VectorSearchPoint("plugin:kanban:$id", rs.getFloat("score"), mapOf("entityType" to "plugin", "pluginId" to pluginId,
                "title" to rs.getString("name"), "text" to rs.getString("text").take(1800), "source" to "Kanban boards",
                "matchMode" to if (rs.getBoolean("keyword")) "Keyword and similarity" else "Similarity",
                "href" to "/plugins/kanban/boards?board=$id"))
        }, vector, query, query, actor.role == "ADMIN", actor.id, limit)
    }
}
