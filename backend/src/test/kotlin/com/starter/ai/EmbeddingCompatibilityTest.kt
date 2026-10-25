package com.starter.ai

import com.starter.dto.AiModelCheckStatus
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class EmbeddingCompatibilityTest {
    private val provider = mock(AiProviderClient::class.java)
    private val service = EmbeddingService(mock(AiSettingsService::class.java), provider)
    private val endpoint = AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "model")
    private val text = listOf("Embedding compatibility check.")
    private fun vector(dimensions: Int) = listOf(List(dimensions) { if (it == 0) 1f else 0f })

    @Test fun `a compatible response confirms observed dimensions with one harmless request`() {
        `when`(provider.embed(endpoint, text, 1536)).thenReturn(vector(1536))
        val result = service.check(endpoint)
        assertThat(result.status).isEqualTo(AiModelCheckStatus.COMPATIBLE)
        assertThat(result.actualDimensions).isEqualTo(1536)
        assertThat(result.requiredDimensions).isEqualTo(1536)
        verify(provider).embed(endpoint, text, 1536)
        verifyNoMoreInteractions(provider)
    }
    @Test fun `native embeddings can suggest omitting an unsupported dimensions parameter`() {
        `when`(provider.embed(endpoint, text, 1536)).thenThrow(AiUnavailableException("Unsupported parameter", 400))
        `when`(provider.embed(endpoint.copy(sendDimensions = false), text, 1536)).thenReturn(vector(1536))
        val result = service.check(endpoint)
        assertThat(result.status).isEqualTo(AiModelCheckStatus.SETTING_CHANGE_REQUIRED)
        assertThat(result.suggestedSendDimensions).isFalse()
        assertThat(result.actualDimensions).isEqualTo(1536)
    }
    @Test fun `a larger native model can suggest requesting the required output size`() {
        val native = endpoint.copy(sendDimensions = false)
        `when`(provider.embed(native, text, 1536)).thenReturn(vector(3072))
        `when`(provider.embed(endpoint, text, 1536)).thenReturn(vector(1536))
        val result = service.check(native)
        assertThat(result.status).isEqualTo(AiModelCheckStatus.SETTING_CHANGE_REQUIRED)
        assertThat(result.suggestedSendDimensions).isTrue()
    }
    @Test fun `models that ignore the requested dimension cannot be marked compatible`() {
        `when`(provider.embed(endpoint, text, 1536)).thenReturn(vector(384))
        `when`(provider.embed(endpoint.copy(sendDimensions = false), text, 1536)).thenReturn(vector(384))
        val result = service.check(endpoint)
        assertThat(result.status).isEqualTo(AiModelCheckStatus.INCOMPATIBLE)
        assertThat(result.actualDimensions).isEqualTo(384)
        assertThat(result.message).contains("384", "1536")
        assertThat(result.suggestedSendDimensions).isNull()
    }
    @Test fun `authentication quota and outage failures remain unverified without extra calls`() {
        for (status in listOf(null, 401, 403, 429, 500)) {
            reset(provider)
            `when`(provider.embed(endpoint, text, 1536)).thenThrow(AiUnavailableException("Unable to verify", status))
            assertThatThrownBy { service.check(endpoint) }.isInstanceOf(AiUnavailableException::class.java)
            verify(provider).embed(endpoint, text, 1536)
            verifyNoMoreInteractions(provider)
        }
    }
    @Test fun `invalid embeddings never receive a compatibility confirmation`() {
        for (values in listOf(emptyList(), listOf(Float.NaN), List(1536) { 0f })) {
            `when`(provider.embed(endpoint, text, 1536)).thenReturn(listOf(values))
            assertThatThrownBy { service.check(endpoint) }.isInstanceOf(AiUnavailableException::class.java).hasMessageContaining("invalid embedding")
        }
    }
    @Test fun `internal matching is verified without calling a provider`() {
        val result = service.check(AiEndpoint(AiProvider.LOCAL, "", LocalTextVectors.MODEL))
        assertThat(result.status).isEqualTo(AiModelCheckStatus.COMPATIBLE)
        assertThat(result.actualDimensions).isEqualTo(1536)
        verifyNoInteractions(provider)
    }
}
