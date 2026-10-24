package com.starter.plugins.packages

import com.starter.access.Roles
import com.starter.ai.LocalTextVectors
import com.starter.model.postgres.User
import com.starter.plugins.PluginView
import com.starter.service.VectorSearchPoint
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class PluginPackageSearch(private val jdbc: JdbcTemplate) {
    fun search(plugins: Collection<PluginView>, query: String, limit: Int, actor: User): List<VectorSearchPoint> {
        val vector = LocalTextVectors.embed(query).joinToString(",", "[", "]")
        return plugins.filter { it.source == "ZIP" }.flatMap { plugin ->
            plugin.manifest.tables.filter { Roles.plugin(actor.role) in (plugin.tableAccess[it.name]?.readRoles ?: emptySet()) }.flatMap { table ->
                jdbc.query("""WITH ranked AS (
                    SELECT id, data, search_text, greatest(coalesce(1 - (search_embedding <=> CAST(? AS vector)), 0),
                        ts_rank_cd(to_tsvector('simple', search_text), plainto_tsquery('simple', ?))) AS score,
                        to_tsvector('simple', search_text) @@ plainto_tsquery('simple', ?) AS keyword
                    FROM "${table.name}" WHERE (? OR owner_id = ?)
                ) SELECT id, data, search_text, score, keyword FROM ranked WHERE keyword OR score >= 0.15 ORDER BY score DESC, id LIMIT ?""",
                    { rs, _ -> VectorSearchPoint("plugin:${plugin.manifest.id}:${rs.getString("id")}", rs.getFloat("score"), mapOf(
                        "entityType" to "plugin", "pluginId" to plugin.manifest.id, "tableName" to table.name,
                        "title" to "${plugin.manifest.name} · ${table.label}", "text" to rs.getString("search_text").take(1800),
                        "source" to plugin.manifest.name, "matchMode" to if (rs.getBoolean("keyword")) "Keyword and similarity" else "Similarity",
                        "href" to "/plugins/${plugin.manifest.id}?record=${rs.getString("id")}&table=${table.name}")) },
                    vector, query, query, Roles.admin(actor.role), actor.id, limit)
            }
        }.sortedByDescending { it.score }.take(limit)
    }
}
