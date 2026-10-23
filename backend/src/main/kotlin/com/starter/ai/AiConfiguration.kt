package com.starter.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

enum class AiProvider { OPENAI_COMPATIBLE, OLLAMA, AZURE_OPENAI, ANTHROPIC, LOCAL }

data class AiEndpoint(val provider: AiProvider, val baseUrl: String, val model: String, val apiKey: String? = null, val sendDimensions: Boolean = true) {
    override fun toString() = "AiEndpoint(provider=$provider, model=$model)"
}
data class ActiveAiConfiguration(val enabled: Boolean, val indexContent: Boolean, val chat: AiEndpoint, val embeddings: AiEndpoint,
    val searchEnabled: Boolean = true, val launcherEnabled: Boolean = true)

@ConfigurationProperties("app.ai")
data class AiEnvironmentProperties(
    var enabled: Boolean = false,
    var indexContent: Boolean = false,
    var chat: EndpointProperties = EndpointProperties(),
    var embeddings: EndpointProperties = EndpointProperties(provider = "LOCAL", baseUrl = "", model = LocalTextVectors.MODEL),
    var searchEnabled: Boolean = true,
    var launcherEnabled: Boolean = true,
) {
    data class EndpointProperties(var provider: String = "OPENAI_COMPATIBLE", var baseUrl: String = "https://api.openai.com/v1", var model: String = "", var apiKey: String = "", var sendDimensions: Boolean = true) {
        fun endpoint() = AiEndpoint(AiProvider.valueOf(provider.trim().uppercase()), baseUrl.trim().trimEnd('/'), if (provider.trim().uppercase() == "LOCAL") LocalTextVectors.MODEL else model.trim(), apiKey.trim().ifBlank { null }, sendDimensions)
        override fun toString() = "EndpointProperties(provider=$provider, model=$model)"
    }
    fun active() = ActiveAiConfiguration(enabled, indexContent, chat.endpoint(), embeddings.endpoint(), searchEnabled, launcherEnabled)
}

@Configuration
@EnableConfigurationProperties(AiEnvironmentProperties::class)
class AiConfiguration
