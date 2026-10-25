package com.starter.ai

import com.starter.dto.*
import com.starter.exception.BadRequestException
import com.starter.model.postgres.AiSettings
import com.starter.repository.postgres.AiSettingsRepository
import com.starter.security.SecretEncryption
import com.starter.service.AuditLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

@Service
class AiSettingsService(
    private val repository: AiSettingsRepository,
    private val environment: AiEnvironmentProperties,
    private val encryption: SecretEncryption,
    private val audit: AuditLogService,
) {
    @Transactional(readOnly = true)
    fun current(): ActiveAiConfiguration {
        val saved = repository.findById(1L).orElse(null)
        return if (saved?.source == "ADMIN") saved.active() else environment.active()
    }

    @Transactional(readOnly = true)
    fun settings(): AiSettingsResponse {
        val saved = repository.findById(1L).orElseGet { AiSettings() }
        return AiSettingsResponse(saved.source, encryption.configured, view(saved.active()), view(environment.active()))
    }

    @Transactional
    fun save(req: AiSettingsRequest, email: String): AiSettingsResponse {
        val saved = repository.findById(1L).orElseGet { AiSettings() }
        if (req.source == "ADMIN") {
            val chat = resolve(req.chat, saved.active().chat)
            val embeddings = resolve(req.embeddings, saved.active().embeddings)
            if (chat.provider == AiProvider.LOCAL) throw BadRequestException("Choose a chat provider")
            if (embeddings.provider == AiProvider.ANTHROPIC) throw BadRequestException("Use an embedding provider or the internal matcher; Anthropic chat does not supply embeddings here")
            validateEndpointUrl(chat)
            if (req.enabled || chat.model.isNotBlank()) validateEndpoint(chat)
            if (embeddings.provider != AiProvider.LOCAL) validateEndpoint(embeddings)
            if (!chat.apiKey.isNullOrBlank() || !embeddings.apiKey.isNullOrBlank()) encryption.requireConfigured()
            saved.enabled = req.enabled
            saved.searchEnabled = req.searchEnabled
            saved.launcherEnabled = req.launcherEnabled
            saved.indexContent = req.indexContent
            saved.chatProvider = chat.provider.name; saved.chatBaseUrl = chat.baseUrl; saved.chatModel = chat.model; saved.chatApiKey = chat.apiKey
            saved.embeddingProvider = embeddings.provider.name; saved.embeddingBaseUrl = embeddings.baseUrl
            saved.embeddingModel = embeddings.model; saved.embeddingApiKey = embeddings.apiKey; saved.embeddingSendDimensions = embeddings.sendDimensions
        }
        saved.source = req.source
        repository.saveAndFlush(saved)
        audit.record(actingEmail = email, action = "AI_SETTINGS_UPDATED", resourceType = "AI_SETTINGS", resourceId = "1")
        return AiSettingsResponse(saved.source, encryption.configured, view(saved.active()), view(environment.active()))
    }

    fun status(): AiSearchStatus = current().let { AiSearchStatus(it.searchEnabled && it.enabled && it.chat.model.isNotBlank(), it.indexContent, it.embeddings.provider,
        searchEnabled = it.searchEnabled, launcherEnabled = it.searchEnabled && it.launcherEnabled) }

    @Transactional(readOnly = true)
    fun endpointForCheck(kind: String, request: AiEndpointCheckRequest): AiEndpoint {
        if (kind !in setOf("chat", "embeddings")) throw BadRequestException("Choose chat or embeddings")
        val config = when (request.source) {
            "ENVIRONMENT" -> environment.active()
            "ADMIN" -> (repository.findById(1L).orElse(null) ?: AiSettings()).active()
            else -> throw BadRequestException("Choose environment or admin settings")
        }
        val previous = if (kind == "chat") config.chat else config.embeddings
        // Draft checks never save credentials or borrow from the other configuration source.
        val endpoint = if (request.source == "ENVIRONMENT") previous else resolve(request.endpoint, previous)
        if (kind == "chat" && endpoint.provider == AiProvider.LOCAL) throw BadRequestException("Choose a chat provider")
        if (kind == "embeddings" && endpoint.provider == AiProvider.ANTHROPIC) throw BadRequestException("Choose an embedding provider; Anthropic supplies chat models here")
        return endpoint
    }

    private fun resolve(req: AiEndpointRequest, previous: AiEndpoint): AiEndpoint {
        if (req.provider == AiProvider.LOCAL) return AiEndpoint(AiProvider.LOCAL, "", LocalTextVectors.MODEL)
        val url = req.baseUrl.trim().trimEnd('/')
        // A stored key is bound to its provider and endpoint, not silently forwarded elsewhere.
        val keep = req.provider == previous.provider && url == previous.baseUrl
        val key = if (req.clearApiKey) null else req.apiKey?.trim()?.ifBlank { null } ?: previous.apiKey?.takeIf { keep }
        return AiEndpoint(req.provider, url, req.model.trim(), key, req.sendDimensions)
    }

    private fun view(config: ActiveAiConfiguration) = AiConfigurationView(config.enabled, config.indexContent, endpointView(config.chat), endpointView(config.embeddings), config.searchEnabled, config.launcherEnabled)
    private fun endpointView(endpoint: AiEndpoint) = AiEndpointView(endpoint.provider, endpoint.baseUrl, endpoint.model, !endpoint.apiKey.isNullOrBlank(), endpoint.sendDimensions)
}

internal fun validateEndpoint(endpoint: AiEndpoint) {
    validateEndpointConnection(endpoint)
    if (endpoint.model.isBlank()) throw BadRequestException("Enter the AI model or Azure deployment name")
}

internal fun validateEndpointConnection(endpoint: AiEndpoint) {
    validateEndpointUrl(endpoint)
    if (endpoint.provider in setOf(AiProvider.AZURE_OPENAI, AiProvider.ANTHROPIC) && endpoint.apiKey.isNullOrBlank()) {
        throw BadRequestException("Configure an API key for this provider")
    }
}

private fun validateEndpointUrl(endpoint: AiEndpoint): URI {
    val uri = runCatching { URI(endpoint.baseUrl) }.getOrNull()
    if (uri == null || uri.scheme !in setOf("http", "https") || uri.host == null || uri.userInfo != null || uri.query != null || uri.fragment != null) {
        throw BadRequestException("Use an HTTP(S) AI base URL without credentials, query parameters, or fragments")
    }
    if (!endpoint.apiKey.isNullOrBlank() && uri.scheme != "https" && uri.host !in setOf("localhost", "127.0.0.1", "::1", "[::1]")) {
        throw BadRequestException("API keys require HTTPS, except for a loopback test server")
    }
    return uri
}
