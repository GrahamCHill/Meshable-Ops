package com.starter.ai

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.security.MessageDigest

class EmbeddingServiceTest {
    @Test fun `local search needs no AI provider or ML service and uses a distinct index identity`() {
        val settings = mock(AiSettingsService::class.java)
        val provider = mock(AiProviderClient::class.java)
        val local = AiEndpoint(AiProvider.LOCAL, "", "old-local-model")
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, false, local, local))
        val service = EmbeddingService(settings, provider)
        val first = service.batch(listOf("Contract renewal notice", ""))
        assertThat(first.vectors).hasSize(2)
        assertThat(EmbeddingDimensions.SIZE).isEqualTo(1536)
        assertThat(first.vectors.all { it.size == EmbeddingDimensions.SIZE && it.all(Float::isFinite) }).isTrue()
        assertThat(first.vectors.first().sumOf { it.toDouble() * it }).isCloseTo(1.0, within(0.00001))
        assertThat(service.batch(listOf("Contract renewal notice")).vectors.single()).isEqualTo(first.vectors.first())
        val overlap = LocalTextVectors.embed("Contract renewal").zip(first.vectors.first()).sumOf { it.first.toDouble() * it.second }
        assertThat(overlap).isGreaterThan(0.3)
        verifyNoInteractions(provider)
    }
    @Test fun `dimension upgrades cannot reuse the old local or external model identity`() {
        val service = EmbeddingService(mock(AiSettingsService::class.java), mock(AiProviderClient::class.java))
        val external = AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "embedding-model")
        val oldIdentities = listOf("local-feature-hash-v2|384", "${external.provider}|${external.baseUrl}|${external.model}|${external.sendDimensions}|384")
            .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } }
        assertThat(service.fingerprint(AiEndpoint(AiProvider.LOCAL, "", LocalTextVectors.MODEL))).isNotIn(oldIdentities)
        assertThat(service.fingerprint(external)).isNotIn(oldIdentities)
    }
    @Test fun `vectors are validated and normalized without falling back to another model`() {
        val settings = mock(AiSettingsService::class.java)
        val provider = mock(AiProviderClient::class.java)
        val endpoint = AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "embedding-model")
        val service = EmbeddingService(settings, provider)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, false, endpoint, endpoint))
        `when`(provider.embed(endpoint, listOf("text"), EmbeddingDimensions.SIZE)).thenReturn(listOf(List(EmbeddingDimensions.SIZE) { if (it == 0) 2f else 0f }))
        assertThat(service.batch(listOf("text")).vectors.single().first()).isEqualTo(1f)
        val originalModel = service.fingerprint(endpoint)
        assertThat(service.fingerprint(endpoint.copy(model = "other-model"))).isNotEqualTo(originalModel)
        `when`(provider.embed(endpoint, listOf("text"), EmbeddingDimensions.SIZE)).thenReturn(listOf(List(384) { 1f }))
        assertThatThrownBy { service.embed(listOf("text")) }.isInstanceOf(AiUnavailableException::class.java).hasMessageContaining("1536")
        `when`(provider.embed(endpoint, listOf("text"), EmbeddingDimensions.SIZE)).thenReturn(listOf(List(EmbeddingDimensions.SIZE) { Float.NaN }))
        assertThatThrownBy { service.embed(listOf("text")) }.isInstanceOf(AiUnavailableException::class.java)
        `when`(provider.embed(endpoint, listOf("text"), EmbeddingDimensions.SIZE)).thenReturn(listOf(List(EmbeddingDimensions.SIZE) { 0f }))
        assertThatThrownBy { service.embed(listOf("text")) }.isInstanceOf(AiUnavailableException::class.java).hasMessageContaining("empty vector")
    }
}
