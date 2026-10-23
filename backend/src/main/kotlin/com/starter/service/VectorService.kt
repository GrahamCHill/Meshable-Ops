package com.starter.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.ai.EmbeddingService
import com.starter.ai.AiUnavailableException
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

data class VectorSearchPoint(
    val id: String,
    val score: Float,
    val payload: Map<String, String>
)

@Service
class VectorService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val embeddings: EmbeddingService,
) {

    fun upsert(pointId: UUID, vector: List<Float>, payload: Map<String, String>, model: String = embeddings.fingerprint()) {
        jdbcTemplate.update(
            """
            INSERT INTO item_vectors (item_id, embedding, payload, embedding_model, updated_at)
            VALUES (?, CAST(? AS vector), CAST(? AS jsonb), ?, NOW())
            ON CONFLICT (item_id) DO UPDATE SET
                embedding = EXCLUDED.embedding,
                payload = EXCLUDED.payload,
                embedding_model = EXCLUDED.embedding_model,
                updated_at = NOW()
            """.trimIndent(),
            pointId,
            vector.toPgVector(),
            objectMapper.writeValueAsString(payload),
            model,
        )
    }

    fun upsertText(pointId: UUID, text: String, payload: Map<String, String>) {
        val batch = embeddings.batch(listOf(text))
        upsert(pointId, batch.vectors.single(), payload, batch.model)
    }

    fun searchText(text: String, limit: Int, filterField: String? = null, filterValue: String? = null): List<VectorSearchPoint> {
        val batch = embeddings.batch(listOf(text))
        return search(batch.vectors.single(), limit, filterField, filterValue, batch.model)
    }

    fun upsertDocument(
        documentId: UUID,
        vector: List<Float>,
        payload: Map<String, String>,
        sourceChecksum: String,
        embeddingModel: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO document_vectors (document_id, embedding, payload, source_checksum, embedding_model, updated_at)
            VALUES (?, CAST(? AS vector), CAST(? AS jsonb), ?, ?, NOW())
            ON CONFLICT (document_id) DO UPDATE SET
                embedding = EXCLUDED.embedding,
                payload = EXCLUDED.payload,
                source_checksum = EXCLUDED.source_checksum,
                embedding_model = EXCLUDED.embedding_model,
                updated_at = NOW()
            """.trimIndent(),
            documentId,
            vector.toPgVector(),
            objectMapper.writeValueAsString(payload),
            sourceChecksum,
            embeddingModel,
        )
    }

    fun search(
        queryVector: List<Float>,
        limit: Int,
        filterField: String? = null,
        filterValue: String? = null,
        model: String = embeddings.fingerprint(),
    ): List<VectorSearchPoint> {
        return try {
            val parameters = mutableListOf<Any>()
            val sql = buildString {
                append(
                    """
                    WITH query_vector AS (SELECT CAST(? AS vector) AS embedding),
                    active_model AS (SELECT CAST(? AS text) AS id),
                    points AS (
                        SELECT item_id::text AS id,
                               (1 - (iv.embedding <=> q.embedding))::real AS score,
                               iv.payload
                        FROM item_vectors iv JOIN items i ON i.id = iv.item_id
                        LEFT JOIN storage_connections sc ON sc.id = i.storage_connection_id
                        CROSS JOIN query_vector q CROSS JOIN active_model m
                        WHERE i.accessible = TRUE AND (sc.id IS NULL OR sc.enabled = TRUE)
                          AND iv.embedding_model = m.id
                        UNION ALL
                        SELECT document_id::text AS id,
                               (1 - (dv.embedding <=> q.embedding))::real AS score,
                               dv.payload
                        FROM document_vectors dv JOIN storage_documents d ON d.id = dv.document_id
                        JOIN items i ON i.id = d.item_id
                        LEFT JOIN storage_connections sc ON sc.id = i.storage_connection_id
                        CROSS JOIN query_vector q CROSS JOIN active_model m
                        WHERE i.accessible = TRUE AND (sc.id IS NULL OR sc.enabled = TRUE)
                          AND dv.embedding_model = m.id
                        UNION ALL
                        SELECT dc.id::text AS id,
                               (1 - (dc.embedding <=> q.embedding))::real AS score,
                               jsonb_build_object('documentId', d.id::text, 'folderId', i.id::text,
                                   'entityType', 'chunk', 'title', d.file_name, 'storagePath', d.storage_path,
                                   'text', dc.content, 'chunkIndex', dc.chunk_index::text) AS payload
                        FROM document_chunks dc JOIN storage_documents d ON d.id = dc.document_id
                        JOIN items i ON i.id = d.item_id
                        LEFT JOIN storage_connections sc ON sc.id = i.storage_connection_id
                        CROSS JOIN query_vector q CROSS JOIN active_model m
                        WHERE i.accessible = TRUE AND (sc.id IS NULL OR sc.enabled = TRUE)
                          AND dc.embedding_model = m.id AND ?
                    )
                    SELECT id, score, payload::text AS payload
                    FROM points
                    """.trimIndent()
                )
                parameters += queryVector.toPgVector()
                parameters += model
                parameters += embeddings.contentEnabled()
                if (filterField != null && filterValue != null) {
                    append(" WHERE payload ->> ? = ?")
                    parameters += filterField
                    parameters += filterValue
                }
                append(" ORDER BY score DESC LIMIT ?")
                parameters += limit
            }

            jdbcTemplate.query(sql, { rs, _ ->
                VectorSearchPoint(
                    id = rs.getString("id"),
                    score = rs.getFloat("score"),
                    payload = objectMapper.readValue(
                        rs.getString("payload"),
                        object : TypeReference<Map<String, String>>() {},
                    ),
                )
            }, *parameters.toTypedArray())
        } catch (_: DataAccessException) {
            throw AiUnavailableException("The search index is unavailable. Check PostgreSQL and run storage reindexing.")
        }
    }

    fun delete(pointId: UUID) {
        jdbcTemplate.update("DELETE FROM item_vectors WHERE item_id = ?", pointId)
    }

    fun replaceChunks(documentId: UUID, chunks: List<String>, vectors: List<List<Float>>, checksum: String, model: String) {
        require(chunks.size == vectors.size)
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId)
        chunks.zip(vectors).forEachIndexed { index, (content, vector) ->
            jdbcTemplate.update("""INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding, source_checksum, embedding_model)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?)""", UUID.randomUUID(), documentId, index, content, vector.toPgVector(), checksum, model)
        }
    }

    fun deleteDocument(documentId: UUID) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId)
        jdbcTemplate.update("DELETE FROM document_vectors WHERE document_id = ?", documentId)
    }

    fun chunksStillAccessible(ids: List<String>): Boolean {
        if (ids.isEmpty()) return false
        val placeholders = ids.joinToString { "?" }
        val count = jdbcTemplate.queryForObject("""SELECT count(*) FROM document_chunks dc
            JOIN storage_documents d ON d.id = dc.document_id JOIN items i ON i.id = d.item_id
            LEFT JOIN storage_connections sc ON sc.id = i.storage_connection_id
            WHERE i.accessible = TRUE AND (sc.id IS NULL OR sc.enabled = TRUE)
            AND dc.embedding_model = ? AND dc.id IN ($placeholders)""", Long::class.java,
            embeddings.fingerprint(), *ids.map(UUID::fromString).toTypedArray()) ?: 0
        return count == ids.distinct().size.toLong()
    }

    private fun List<Float>.toPgVector() = joinToString(",", "[", "]")
}
