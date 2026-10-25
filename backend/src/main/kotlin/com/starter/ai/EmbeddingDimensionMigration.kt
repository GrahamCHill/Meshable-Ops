package com.starter.ai

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Runs after supplemental schema setup and before indexing jobs become ready. */
@Component
@Order(10)
class EmbeddingDimensionMigration(private val jdbc: JdbcTemplate) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('embedding-dimension-migration'))", RowCallbackHandler { })
        val columns = jdbc.query("""
            SELECT c.relname, a.attname FROM pg_attribute a
            JOIN pg_class c ON c.oid = a.attrelid JOIN pg_type t ON t.oid = a.atttypid
            WHERE c.relnamespace = current_schema()::regnamespace AND c.relkind = 'r'
                AND NOT a.attisdropped AND t.typname = 'vector' AND a.atttypmod <> ?
                AND ((c.relname IN ('item_vectors', 'document_vectors', 'document_chunks') AND a.attname = 'embedding')
                    OR (c.relname ~ '^plugin_[a-z0-9_]+$' AND a.attname = 'search_embedding'))
            ORDER BY c.relname
        """.trimIndent(), { rs, _ -> rs.getString("relname") to rs.getString("attname") }, EmbeddingDimensions.SIZE)
        columns.forEach { (table, column) ->
            // Only the fixed host-owned columns selected above are interpolated as identifiers.
            // Preserve metadata and extracted text for keyword search while vectors await reindexing.
            jdbc.execute("ALTER TABLE \"$table\" ALTER COLUMN $column DROP NOT NULL")
            jdbc.execute("ALTER TABLE \"$table\" ALTER COLUMN $column TYPE vector(${EmbeddingDimensions.SIZE}) USING NULL::vector(${EmbeddingDimensions.SIZE})")
            if (column == "search_embedding") {
                rebuildLocalVectors(table)
            } else {
                jdbc.update("UPDATE \"$table\" SET embedding_model = 'requires-reindex-${EmbeddingDimensions.SIZE}'")
            }
        }
        if (columns.any { it.first in setOf("document_vectors", "document_chunks") }) {
            jdbc.update("UPDATE storage_documents SET embedding_model = NULL, vectorized_at = NULL")
        }
        if (columns.isNotEmpty()) LoggerFactory.getLogger(javaClass).info(
            "Migrated {} search vector columns to {} dimensions; run storage reindexing to rebuild document vectors",
            columns.size, EmbeddingDimensions.SIZE)
    }

    private fun rebuildLocalVectors(table: String) {
        // Existing Kanban boards may predate search_text; retain the same fallback used by search.
        val text = if (table == "plugin_kanban_boards") "coalesce(nullif(search_text, ''), name || ' ' || content_json)" else "search_text"
        var after: UUID? = null
        do {
            val rows = if (after == null) {
                jdbc.query("SELECT id, $text AS text FROM \"$table\" ORDER BY id LIMIT 200",
                    { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("text") })
            } else {
                jdbc.query("SELECT id, $text AS text FROM \"$table\" WHERE id > ? ORDER BY id LIMIT 200",
                    { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("text") }, after)
            }
            jdbc.batchUpdate("UPDATE \"$table\" SET search_embedding = CAST(? AS vector) WHERE id = ?", rows, 200) { statement, row ->
                statement.setString(1, LocalTextVectors.embed(row.second).joinToString(",", "[", "]"))
                statement.setObject(2, row.first)
            }
            after = rows.lastOrNull()?.first
        } while (rows.size == 200)
    }
}
