package com.starter.ai

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class EmbeddingServiceTest {
    @Test fun `local search needs no AI provider or ML service and uses a distinct index identity`() {
        val settings = mock(AiSettingsService::class.java)
        val provider = mock(AiProviderClient::class.java)
        val local = AiEndpoint(AiProvider.LOCAL, "", "old-local-model")
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, false, local, local))
        val service = EmbeddingService(settings, provider)
        val first = service.batch(listOf("Contract renewal notice", ""))
        assertThat(first.vectors).hasSize(2)
        assertThat(first.vectors.all { it.size == 384 && it.all(Float::isFinite) }).isTrue()
        assertThat(first.vectors.first().sumOf { it.toDouble() * it }).isCloseTo(1.0, within(0.00001))
        assertThat(service.batch(listOf("Contract renewal notice")).vectors.single()).isEqualTo(first.vectors.first())
        val overlap = LocalTextVectors.embed("Contract renewal").zip(first.vectors.first()).sumOf { it.first.toDouble() * it.second }
        assertThat(overlap).isGreaterThan(0.3)
        verifyNoInteractions(provider)
    }
    @Test fun `vectors are validated and normalized without falling back to another model`() {
        val settings = mock(AiSettingsService::class.java)
        val provider = mock(AiProviderClient::class.java)
        val endpoint = AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "embedding-model")
        val service = EmbeddingService(settings, provider)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(false, false, endpoint, endpoint))
        `when`(provider.embed(endpoint, listOf("text"), 384)).thenReturn(listOf(List(384) { if (it == 0) 2f else 0f }))
        assertThat(service.batch(listOf("text")).vectors.single().first()).isEqualTo(1f)
        val originalModel = service.fingerprint(endpoint)
        assertThat(service.fingerprint(endpoint.copy(model = "other-model"))).isNotEqualTo(originalModel)
        `when`(provider.embed(endpoint, listOf("text"), 384)).thenReturn(listOf(List(768) { 1f }))
        assertThatThrownBy { service.embed(listOf("text")) }.isInstanceOf(AiUnavailableException::class.java).hasMessageContaining("384")
        `when`(provider.embed(endpoint, listOf("text"), 384)).thenReturn(listOf(List(384) { Float.NaN }))
        assertThatThrownBy { service.embed(listOf("text")) }.isInstanceOf(AiUnavailableException::class.java)
    }
}
