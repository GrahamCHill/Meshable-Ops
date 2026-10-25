package com.starter.ai

import com.fasterxml.jackson.databind.JsonNode
import com.starter.dto.AiModelView
import com.starter.dto.AiModelsResponse
import com.starter.exception.BadRequestException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

class AiUnavailableException(message: String, val statusCode: Int? = null) : RuntimeException(message)

@Component
class AiProviderClient(@Qualifier("aiRestTemplate") private val http: RestTemplate) {
    fun models(endpoint: AiEndpoint): AiModelsResponse {
        if (endpoint.provider == AiProvider.LOCAL) return AiModelsResponse(listOf(AiModelView(LocalTextVectors.MODEL, "Internal text matcher")), "The internal matcher is available without an API key.")
        validateEndpointConnection(endpoint)
        val models = linkedMapOf<String, AiModelView>()
        var cursor: String? = null
        var more = false
        var pages = 0
        do {
            val route = if (endpoint.provider == AiProvider.ANTHROPIC) {
                UriComponentsBuilder.fromPath("models").queryParam("limit", 100).apply {
                    cursor?.let { queryParam("after_id", it) }
                }.build().encode().toUriString()
            } else "models"
            val response = request(endpoint, HttpMethod.GET, route)
            val entries = response.path("data")
            if (!entries.isArray) throw AiUnavailableException("The provider returned an invalid model list. Enter a model name manually and check it.")
            entries.take(500).forEach { entry ->
                val id = entry.path("id").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() && it.length <= 200 }
                if (id != null) models[id] = AiModelView(id, entry.path("display_name").takeIf { it.isTextual }?.asText()?.take(200)?.ifBlank { id } ?: id)
            }
            more = response.path("has_more").asBoolean(false) || entries.size() > 500
            val next = response.path("last_id").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() && it.length <= 200 }
            pages++
            if (endpoint.provider != AiProvider.ANTHROPIC || !more || next == null || next == cursor) break
            cursor = next
        } while (pages < 5 && models.size < 500)
        val message = when {
            endpoint.provider == AiProvider.AZURE_OPENAI -> "Azure lists model IDs. Use your deployment name if it differs, then check the selected model."
            models.isEmpty() -> "No models were listed. You can enter a model name manually and check it."
            else -> "Choose an available model or enter a name manually. Check the selected model to confirm it supports this task."
        }
        return AiModelsResponse(models.values.sortedBy { it.id }.take(500), message, more || models.size > 500)
    }

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

    private fun post(endpoint: AiEndpoint, route: String, body: Any) = request(endpoint, HttpMethod.POST, route, body)

    private fun request(endpoint: AiEndpoint, method: HttpMethod, route: String, body: Any? = null): JsonNode {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            when (endpoint.provider) {
                AiProvider.AZURE_OPENAI -> set("api-key", endpoint.apiKey!!)
                AiProvider.ANTHROPIC -> { set("x-api-key", endpoint.apiKey!!); set("anthropic-version", "2023-06-01") }
                else -> endpoint.apiKey?.let { setBearerAuth(it) }
            }
        }
        try {
            return http.exchange("${endpoint.baseUrl}/$route", method, HttpEntity(body, headers), JsonNode::class.java).body
                ?: throw AiUnavailableException("The AI provider returned an empty response")
        } catch (ex: RestClientResponseException) {
            // Provider response bodies can echo keys, prompts, or retrieved content.
            val message = when (ex.statusCode.value()) {
                401, 403 -> "The AI provider rejected authentication. Check the configured key and model access."
                429 -> "The AI provider is rate limited or out of quota. Try again later."
                else -> if (method == HttpMethod.GET) "The provider could not list models. Check the API URL and key, or enter a model name manually."
                    else "The AI provider rejected the request. Check the endpoint, model, and embedding dimensions."
            }
            throw AiUnavailableException(message, ex.statusCode.value())
        } catch (_: RestClientException) {
            throw AiUnavailableException("The AI provider could not be reached within the request timeout")
        }
    }
}
