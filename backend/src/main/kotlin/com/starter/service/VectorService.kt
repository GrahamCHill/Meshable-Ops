package com.starter.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.starter.config.QdrantConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.UUID
import kotlin.math.sqrt
import kotlin.random.Random

private val log = LoggerFactory.getLogger(VectorService::class.java)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QdrantSearchResponse(val result: List<QdrantScoredPoint> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class QdrantScoredPoint(
    val id: String = "",
    val score: Float = 0f,
    val payload: Map<String, String> = emptyMap()
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
    private val rest: RestTemplate,
    private val qdrantConfig: QdrantConfig
) {
    @Value("\${qdrant.base-url:http://qdrant:6333}") private lateinit var baseUrl: String
    @Value("\${qdrant.collection-name}") private lateinit var collection: String
    @Value("\${ml.base-url:http://ml-backend:8000}") private lateinit var mlBaseUrl: String

    private val headers get() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
    private var collectionReady = false

    private fun ensureCollection() {
        if (!collectionReady) {
            qdrantConfig.ensureCollection(rest)
            collectionReady = true
        }
    }

    fun upsert(pointId: UUID, vector: List<Float>, payload: Map<String, String>) {
        ensureCollection()
        try {
            val payloadJson = payload.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
            val body = """
                {"points":[
                  {
                    "id":"$pointId",
                    "vector":${vector.joinToString(",", "[", "]")},
                    "payload":{$payloadJson}
                  }
                ]}
            """.trimIndent()
            rest.exchange(
                "$baseUrl/collections/$collection/points?wait=true",
                HttpMethod.PUT,
                HttpEntity(body, headers),
                String::class.java
            )
        } catch (e: Exception) {
            log.error("Qdrant upsert failed for $pointId: ${e.message}")
        }
    }

    fun search(
        queryVector: List<Float>,
        limit: Int,
        filterField: String? = null,
        filterValue: String? = null
    ): List<QdrantScoredPoint> {
        ensureCollection()
        return try {
            val filterClause = if (filterField != null && filterValue != null) {
                ""","filter":{"must":[{"key":"$filterField","match":{"value":"$filterValue"}}]}"""
            } else ""

            val body = """
                {
                  "vector":${queryVector.joinToString(",", "[", "]")},
                  "limit":$limit,
                  "with_payload":true
                  $filterClause
                }
            """.trimIndent()

            val response = rest.exchange(
                "$baseUrl/collections/$collection/points/search",
                HttpMethod.POST,
                HttpEntity(body, headers),
                QdrantSearchResponse::class.java
            )
            response.body?.result ?: emptyList()
        } catch (e: Exception) {
            log.error("Qdrant search failed: ${e.message}")
            emptyList()
        }
    }

    fun delete(pointId: UUID) {
        ensureCollection()
        try {
            val body = """{"points":["$pointId"]}"""
            rest.exchange(
                "$baseUrl/collections/$collection/points/delete?wait=true",
                HttpMethod.POST,
                HttpEntity(body, headers),
                String::class.java
            )
        } catch (e: Exception) {
            log.error("Qdrant delete failed for $pointId: ${e.message}")
        }
    }

    fun embedText(text: String, size: Int): List<Float> {
        val normalized = text.trim().ifBlank { "empty" }
        return try {
            val response = rest.postForEntity(
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
}
