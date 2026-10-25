package com.starter.ai

import com.starter.dto.AiModelCheckResponse
import com.starter.dto.AiModelCheckStatus
import org.springframework.stereotype.Service
import java.security.MessageDigest
import kotlin.math.sqrt

@Service
class EmbeddingService(
    private val settings: AiSettingsService,
    private val provider: AiProviderClient,
) {
    data class Batch(val vectors: List<List<Float>>, val model: String)

    fun fingerprint() = fingerprint(settings.current().embeddings)
    fun fingerprint(endpoint: AiEndpoint): String {
        val identity = if (endpoint.provider == AiProvider.LOCAL) LocalTextVectors.MODEL else "${endpoint.provider}|${endpoint.baseUrl}|${endpoint.model}|${endpoint.sendDimensions}"
        return MessageDigest.getInstance("SHA-256").digest("$identity|${EmbeddingDimensions.SIZE}".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun batch(texts: List<String>): Batch {
        val endpoint = settings.current().embeddings
        return Batch(embed(texts, endpoint), fingerprint(endpoint))
    }

    fun contentEnabled() = settings.current().indexContent

    fun check(endpoint: AiEndpoint): AiModelCheckResponse {
        val required = EmbeddingDimensions.SIZE
        if (endpoint.provider == AiProvider.LOCAL) return AiModelCheckResponse(AiModelCheckStatus.COMPATIBLE, LocalTextVectors.MODEL,
            "The internal matcher returns $required dimensions and is ready for search.", required, required)
        fun probe(candidate: AiEndpoint): List<Float> {
            val vectors = provider.embed(candidate, listOf("Embedding compatibility check."), required)
            if (vectors.size != 1) throw AiUnavailableException("The embedding service returned an incomplete test response")
            val vector = vectors.single()
            if (vector.isEmpty() || vector.any { !it.isFinite() } || vector.none { it != 0f }) throw AiUnavailableException("The model returned an invalid embedding. Choose another embedding model.")
            return vector
        }
        var firstError: AiUnavailableException? = null
        val first = try { probe(endpoint) } catch (failure: AiUnavailableException) {
            // Authentication, quota, transport, and server failures do not establish incompatibility.
            if (failure.statusCode !in setOf(400, 404, 422)) throw failure
            firstError = failure
            null
        }
        if (first?.size == required) return AiModelCheckResponse(AiModelCheckStatus.COMPATIBLE, endpoint.model,
            "Verified: this model returned $required dimensions with the current settings. No storage documents were sent.", required, required)
        val alternative = try { probe(endpoint.copy(sendDimensions = !endpoint.sendDimensions)) } catch (failure: AiUnavailableException) {
            if (failure.statusCode !in setOf(400, 404, 422)) throw failure
            if (first == null) throw (firstError ?: failure)
            null
        }
        if (alternative?.size == required) {
            val action = if (endpoint.sendDimensions) "Disable" else "Enable"
            return AiModelCheckResponse(AiModelCheckStatus.SETTING_CHANGE_REQUIRED, endpoint.model,
                "$action Request $required dimensions: the model returned $required values with that setting. Apply the suggested setting before saving.",
                required, required, !endpoint.sendDimensions)
        }
        val actual = first?.size ?: alternative?.size
        return AiModelCheckResponse(AiModelCheckStatus.INCOMPATIBLE, endpoint.model,
            "This model returned $actual dimensions; search requires $required. Checking with and without the dimensions parameter did not produce a compatible vector. Choose another embedding model.",
            required, actual)
    }

    fun embed(texts: List<String>) = embed(texts, settings.current().embeddings)
    fun embed(texts: List<String>, endpoint: AiEndpoint): List<List<Float>> {
        require(texts.isNotEmpty() && texts.size <= 64)
        val normalized = texts.map { it.trim().ifEmpty { "empty" }.take(12000) }
        val vectors = if (endpoint.provider == AiProvider.LOCAL) {
            normalized.map(LocalTextVectors::embed)
        } else provider.embed(endpoint, normalized, EmbeddingDimensions.SIZE)
        if (vectors.size != normalized.size) throw AiUnavailableException("The embedding service returned an incomplete batch")
        return vectors.map { vector ->
            if (vector.size != EmbeddingDimensions.SIZE) throw AiUnavailableException("The embedding model returned ${vector.size} dimensions; search requires ${EmbeddingDimensions.SIZE}. Check the model and its dimensions setting in App Intelligence.")
            if (vector.any { !it.isFinite() }) throw AiUnavailableException("The embedding model must return ${EmbeddingDimensions.SIZE} finite dimensions.")
            val norm = sqrt(vector.sumOf { it.toDouble() * it })
            if (norm <= 0.0) throw AiUnavailableException("The embedding service returned an empty vector")
            vector.map { (it / norm).toFloat() }
        }
    }
}
