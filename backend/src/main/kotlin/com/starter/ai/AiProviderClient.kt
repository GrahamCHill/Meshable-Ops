package com.starter.ai

import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.BadRequestException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate

class AiUnavailableException(message: String) : RuntimeException(message)

@Component
class AiProviderClient(@Qualifier("aiRestTemplate") private val http: RestTemplate) {
    fun answer(endpoint: AiEndpoint, instructions: String, question: String): String {
        validateEndpoint(endpoint)
        val anthropic = endpoint.provider == AiProvider.ANTHROPIC
        val body = if (anthropic) mapOf(
            "model" to endpoint.model, "max_tokens" to 1200, "system" to instructions,
            "messages" to listOf(mapOf("role" to "user", "content" to question)),
        ) else mapOf(
            "model" to endpoint.model, "stream" to false,
            (if (endpoint.provider == AiProvider.OLLAMA) "max_tokens" else "max_completion_tokens") to 1200,
            "messages" to listOf(mapOf("role" to "system", "content" to instructions), mapOf("role" to "user", "content" to question)),
        )
        val response = post(endpoint, if (anthropic) "messages" else "chat/completions", body)
        val answer = if (anthropic) response.path("content").filter { it.path("type").asText() == "text" }.joinToString("\n") { it.path("text").asText() }
            else response.path("choices").firstOrNull()?.path("message")?.path("content")?.takeIf { it.isTextual }?.asText().orEmpty()
        if (answer.isBlank()) throw AiUnavailableException("The AI provider returned no text answer. Check the selected model.")
        return answer.take(20000)
    }

    fun embed(endpoint: AiEndpoint, texts: List<String>, dimensions: Int): List<List<Float>> {
        validateEndpoint(endpoint)
        if (endpoint.provider in setOf(AiProvider.LOCAL, AiProvider.ANTHROPIC)) throw BadRequestException("Choose a compatible embedding provider")
        val body = mutableMapOf<String, Any>("model" to endpoint.model, "input" to texts, "encoding_format" to "float")
        if (endpoint.sendDimensions) body["dimensions"] = dimensions
        val response = post(endpoint, "embeddings", body)
        val data = response.path("data")
        if (!data.isArray || data.size() != texts.size) throw AiUnavailableException("The embedding provider returned an incomplete batch")
        val indexed = data.toList().sortedBy { it.path("index").asInt(-1) }
        if (indexed.map { it.path("index").asInt(-1) } != texts.indices.toList()) throw AiUnavailableException("The embedding provider returned invalid indices")
        return indexed.map { entry ->
            val values = entry.path("embedding")
            if (!values.isArray || values.any { !it.isNumber }) throw AiUnavailableException("The embedding provider returned invalid vectors")
            values.map { it.floatValue() }
        }
    }

    private fun post(endpoint: AiEndpoint, route: String, body: Any): JsonNode {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            when (endpoint.provider) {
                AiProvider.AZURE_OPENAI -> set("api-key", endpoint.apiKey!!)
                AiProvider.ANTHROPIC -> { set("x-api-key", endpoint.apiKey!!); set("anthropic-version", "2023-06-01") }
                else -> endpoint.apiKey?.let { setBearerAuth(it) }
            }
        }
        try {
            return http.postForObject("${endpoint.baseUrl}/$route", HttpEntity(body, headers), JsonNode::class.java)
                ?: throw AiUnavailableException("The AI provider returned an empty response")
        } catch (ex: RestClientResponseException) {
            // Provider response bodies can echo keys, prompts, or retrieved content.
            val message = when (ex.statusCode.value()) {
                401, 403 -> "The AI provider rejected authentication. Check the configured key and model access."
                429 -> "The AI provider is rate limited or out of quota. Try again later."
                else -> "The AI provider rejected the request. Check the endpoint, model, and embedding dimensions."
            }
            throw AiUnavailableException(message)
        } catch (_: RestClientException) {
            throw AiUnavailableException("The AI provider could not be reached within the request timeout")
        }
    }
}
