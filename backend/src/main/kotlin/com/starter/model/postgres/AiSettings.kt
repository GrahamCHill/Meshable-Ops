package com.starter.model.postgres

import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.ai.*
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "ai_settings")
class AiSettings(
    @Id val id: Long = 1,
    var source: String = "ENVIRONMENT",
    var enabled: Boolean = false,
    @Column(name = "search_enabled", nullable = false) @ColumnDefault("true") var searchEnabled: Boolean = true,
    @Column(name = "launcher_enabled", nullable = false) @ColumnDefault("true") var launcherEnabled: Boolean = true,
    @Column(name = "index_content") var indexContent: Boolean = false,
    @Column(name = "chat_provider") var chatProvider: String = "OPENAI_COMPATIBLE",
    @Column(name = "chat_base_url", columnDefinition = "TEXT") var chatBaseUrl: String = "https://api.openai.com/v1",
    @Column(name = "chat_model") var chatModel: String = "",
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(name = "chat_api_key", columnDefinition = "TEXT") var chatApiKey: String? = null,
    @Column(name = "embedding_provider") var embeddingProvider: String = "LOCAL",
    @Column(name = "embedding_base_url", columnDefinition = "TEXT") var embeddingBaseUrl: String = "",
    @Column(name = "embedding_model") var embeddingModel: String = LocalTextVectors.MODEL,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(name = "embedding_api_key", columnDefinition = "TEXT") var embeddingApiKey: String? = null,
    @Column(name = "embedding_send_dimensions") var embeddingSendDimensions: Boolean = true,
) {
    fun active() = ActiveAiConfiguration(enabled, indexContent,
        AiEndpoint(AiProvider.valueOf(chatProvider), chatBaseUrl, chatModel, chatApiKey),
        AiEndpoint(AiProvider.valueOf(embeddingProvider), embeddingBaseUrl, if (embeddingProvider == "LOCAL") LocalTextVectors.MODEL else embeddingModel, embeddingApiKey, embeddingSendDimensions),
        searchEnabled, launcherEnabled)
    override fun toString() = "AiSettings(id=$id, source=$source, enabled=$enabled)"
}
