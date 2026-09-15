package com.starter.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.UUID
import kotlin.math.sqrt
import kotlin.random.Random

private val log = LoggerFactory.getLogger(VectorService::class.java)

data class VectorSearchPoint(
    val id: String,
    val score: Float,
    val payload: Map<String, String>
)

data class MlVectorizeRequest(
    val texts: List<String>,
    val dimensions: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MlVectorizeResponse(
    val dimensions: Int = 0,
    val count: Int = 0,
    val vectors: List<List<Float>> = emptyList()
)

@Service
class VectorService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate,
) {
    @Value("\${ml.base-url:http://ml-backend:8000}") private lateinit var mlBaseUrl: String

    private val headers get() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    fun upsert(pointId: UUID, vector: List<Float>, payload: Map<String, String>) {
        jdbcTemplate.update(
            """
            INSERT INTO item_vectors (item_id, embedding, payload, updated_at)
            VALUES (?, CAST(? AS vector), CAST(? AS jsonb), NOW())
            ON CONFLICT (item_id) DO UPDATE SET
                embedding = EXCLUDED.embedding,
                payload = EXCLUDED.payload,
                updated_at = NOW()
            """.trimIndent(),
            pointId,
            vector.toPgVector(),
            objectMapper.writeValueAsString(payload),
        )
    }

    fun search(
        queryVector: List<Float>,
        limit: Int,
        filterField: String? = null,
        filterValue: String? = null
    ): List<VectorSearchPoint> {
        return try {
            val parameters = mutableListOf<Any>()
            val sql = buildString {
                append(
                    """
                    SELECT item_id::text AS id,
                           (1 - (embedding <=> CAST(? AS vector)))::real AS score,
                           payload::text AS payload
                    FROM item_vectors
                    """.trimIndent()
                )
                parameters += queryVector.toPgVector()
                if (filterField != null && filterValue != null) {
                    append(" WHERE payload ->> ? = ?")
                    parameters += filterField
                    parameters += filterValue
                }
                append(" ORDER BY embedding <=> CAST(? AS vector) LIMIT ?")
                parameters += queryVector.toPgVector()
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
        } catch (e: Exception) {
            log.error("pgvector search failed: ${e.message}")
            emptyList()
        }
    }

    fun delete(pointId: UUID) {
        jdbcTemplate.update("DELETE FROM item_vectors WHERE item_id = ?", pointId)
    }

    fun embedText(text: String, size: Int): List<Float> {
        val normalized = text.trim().ifBlank { "empty" }
        return try {
            val response = restTemplate.postForEntity(
                "$mlBaseUrl/api/v1/ml/vectorize",
                HttpEntity(MlVectorizeRequest(listOf(normalized), size), headers),
                MlVectorizeResponse::class.java
            )
            val vector = response.body?.vectors?.firstOrNull()
            if (vector != null && vector.size == size) {
                vector
            } else {
                log.warn("ML backend returned no usable vector, falling back to stub embedding")
                stubEmbed(normalized, size)
            }
        } catch (e: Exception) {
            log.warn("ML embedding failed, falling back to stub embedding: ${e.message}")
            stubEmbed(normalized, size)
        }
    }

    fun stubEmbed(text: String, size: Int): List<Float> {
        val rng = Random(text.hashCode().toLong())
        val raw = FloatArray(size) { rng.nextFloat() * 2f - 1f }
        val norm = sqrt(raw.sumOf { (it * it).toDouble() }.toFloat())
        return raw.map { it / norm }
    }

    private fun List<Float>.toPgVector() = joinToString(",", "[", "]")
}
