package com.starter.ai

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
        return MessageDigest.getInstance("SHA-256").digest("$identity|384".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun batch(texts: List<String>): Batch {
        val endpoint = settings.current().embeddings
        return Batch(embed(texts, endpoint), fingerprint(endpoint))
    }

    fun contentEnabled() = settings.current().indexContent

    fun embed(texts: List<String>) = embed(texts, settings.current().embeddings)
    fun embed(texts: List<String>, endpoint: AiEndpoint): List<List<Float>> {
        require(texts.isNotEmpty() && texts.size <= 64)
        val normalized = texts.map { it.trim().ifEmpty { "empty" }.take(12000) }
        val vectors = if (endpoint.provider == AiProvider.LOCAL) {
            normalized.map(LocalTextVectors::embed)
        } else provider.embed(endpoint, normalized, 384)
        if (vectors.size != normalized.size) throw AiUnavailableException("The embedding service returned an incomplete batch")
        return vectors.map { vector ->
            if (vector.size != 384 || vector.any { !it.isFinite() }) throw AiUnavailableException("The embedding model must return 384 finite dimensions. Choose a compatible model or enable its dimensions parameter.")
            val norm = sqrt(vector.sumOf { it.toDouble() * it })
            if (norm <= 0.0) throw AiUnavailableException("The embedding service returned an empty vector")
            vector.map { (it / norm).toFloat() }
        }
    }
}
