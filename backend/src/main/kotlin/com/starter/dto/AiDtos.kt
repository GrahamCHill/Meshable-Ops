package com.starter.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.starter.ai.AiProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.*

data class AiEndpointRequest(
    val provider: AiProvider,
    @field:Size(max = 512) val baseUrl: String = "",
    @field:Size(max = 200) val model: String = "",
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @field:Size(max = 4096) val apiKey: String? = null,
    val clearApiKey: Boolean = false,
    val sendDimensions: Boolean = true,
) { override fun toString() = "AiEndpointRequest(provider=$provider, model=$model)" }
data class AiSettingsRequest(
    @field:Pattern(regexp = "ENVIRONMENT|ADMIN") val source: String,
    val enabled: Boolean,
    val indexContent: Boolean,
    @field:Valid val chat: AiEndpointRequest,
    @field:Valid val embeddings: AiEndpointRequest,
    val searchEnabled: Boolean = true,
    val launcherEnabled: Boolean = true,
)
data class AiEndpointView(val provider: AiProvider, val baseUrl: String, val model: String, val apiKeyConfigured: Boolean, val sendDimensions: Boolean)
data class AiConfigurationView(val enabled: Boolean, val indexContent: Boolean, val chat: AiEndpointView, val embeddings: AiEndpointView,
    val searchEnabled: Boolean, val launcherEnabled: Boolean)
data class AiSettingsResponse(val source: String, val encryptionReady: Boolean, val admin: AiConfigurationView, val environment: AiConfigurationView)
data class AiSearchStatus(val enabled: Boolean, val indexContent: Boolean, val embeddingProvider: AiProvider, val dimensions: Int = 384,
    val searchEnabled: Boolean = true, val launcherEnabled: Boolean = true)
data class AiQuestionRequest(@field:NotBlank @field:Size(max = 4000) val query: String, @field:Min(1) @field:Max(8) val limit: Int = 5)
data class AiSource(val number: Int, val pointId: String, val documentId: String, val folderId: String, val title: String, val path: String, val excerpt: String, val score: Float)
data class AiAnswerResponse(val answer: String, val sources: List<AiSource>, val model: String?, val grounded: Boolean)
