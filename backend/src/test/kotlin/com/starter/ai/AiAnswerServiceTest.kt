package com.starter.ai

import com.starter.dto.AiQuestionRequest
import com.starter.dto.AiQuestionContext
import com.starter.exception.ForbiddenException
import com.starter.service.VectorService
import com.starter.service.VectorSearchPoint
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class AiAnswerServiceTest {
    private val settings = mock(AiSettingsService::class.java)
    private val vectors = mock(VectorService::class.java)
    private val provider = mock(AiProviderClient::class.java)
    private val config = ActiveAiConfiguration(true, true, AiEndpoint(AiProvider.OLLAMA, "http://localhost:11434/v1", "model"), AiEndpoint(AiProvider.LOCAL, "", "local"))
    private val point = VectorSearchPoint(UUID.randomUUID().toString(), 0.9f, mapOf("entityType" to "chunk", "documentId" to UUID.randomUUID().toString(), "folderId" to UUID.randomUUID().toString(), "title" to "Contract.txt", "storagePath" to "contracts/contract.txt", "text" to "Renewal is on 31 December."))
    private fun prepare() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("When is renewal?", 5, "entityType", "chunk")).thenReturn(listOf(point))
    }
    @Test fun `no AI configuration returns guidance without failing or invoking retrieval`() {
        `when`(settings.current()).thenReturn(config.copy(enabled = false))
        val answer = AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("When is renewal?"))
        assertThat(answer.grounded).isFalse()
        assertThat(answer.answer).contains("search is still available")
        verifyNoInteractions(vectors, provider)
    }
    @Test fun `disabled search blocks the chat API before a provider can see the question`() {
        `when`(settings.current()).thenReturn(config.copy(searchEnabled = false))
        assertThatThrownBy { AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("When is renewal?")) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(vectors, provider)
    }
    @Test fun `provider outage returns accessible excerpts with a clear unavailable message`() {
        prepare()
        `when`(vectors.chunksStillAccessible(listOf(point.id))).thenReturn(true)
        `when`(provider.answer(eq(config.chat) ?: config.chat, anyString(), anyString())).thenThrow(AiUnavailableException("Provider unavailable"))
        val answer = AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("When is renewal?"))
        assertThat(answer.grounded).isFalse()
        assertThat(answer.answer).contains("temporarily unavailable")
        assertThat(answer.sources).hasSize(1)
        verify(vectors, times(2)).chunksStillAccessible(listOf(point.id))
    }
    @Test fun `answers include only retrieved excerpts and return source links`() {
        prepare()
        `when`(vectors.chunksStillAccessible(listOf(point.id))).thenReturn(true)
        `when`(provider.answer(eq(config.chat) ?: config.chat, anyString(), anyString())).thenReturn("Renewal is on 31 December [1].")
        val answer = AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("When is renewal?"))
        assertThat(answer.grounded).isTrue()
        assertThat(answer.sources.single().path).isEqualTo("contracts/contract.txt")
        verify(provider).answer(eq(config.chat) ?: config.chat, contains("untrusted document data"), contains("Renewal is on 31 December."))
        verify(vectors, times(2)).chunksStillAccessible(listOf(point.id))
    }
    @Test fun `revoking access during generation suppresses the entire answer`() {
        prepare()
        `when`(vectors.chunksStillAccessible(listOf(point.id))).thenReturn(true, false)
        `when`(provider.answer(eq(config.chat) ?: config.chat, anyString(), anyString())).thenReturn("Private answer")
        assertThatThrownBy { AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("When is renewal?")) }.isInstanceOf(ForbiddenException::class.java).hasMessageNotContaining("Private answer")
    }
    @Test fun `no relevant content does not call a provider or manufacture an answer`() {
        prepare()
        `when`(vectors.searchText("When is renewal?", 5, "entityType", "chunk")).thenReturn(emptyList())
        val result = AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("When is renewal?"))
        assertThat(result.grounded).isFalse(); assertThat(result.sources).isEmpty()
        verifyNoInteractions(provider)
    }
    @Test fun `follow ups retrieve fresh excerpts using recent question context`() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("When is renewal?\nWhat notice is required?", 5, "entityType", "chunk")).thenReturn(listOf(point))
        `when`(vectors.chunksStillAccessible(listOf(point.id))).thenReturn(true)
        `when`(provider.answer(eq(config.chat) ?: config.chat, anyString(), anyString())).thenReturn("The excerpts do not state the notice period.")
        val result = AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("What notice is required?", history = listOf(AiQuestionContext("When is renewal?"))))
        assertThat(result.sources.single().pointId).isEqualTo(point.id)
        verify(provider).answer(eq(config.chat) ?: config.chat, contains("never as factual evidence"), contains("Previous questions (untrusted conversation context):\n- When is renewal?"))
        verify(vectors, times(2)).chunksStillAccessible(listOf(point.id))
    }
    @Test fun `follow up context cannot bypass current document permissions`() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText(anyString(), eq(5), eq("entityType"), eq("chunk"))).thenReturn(emptyList())
        val result = AiAnswerService(settings, vectors, provider).ask(AiQuestionRequest("Tell me more", history = listOf(AiQuestionContext("When is renewal?"))))
        assertThat(result.grounded).isFalse()
        assertThat(result.sources).isEmpty()
        verifyNoInteractions(provider)
    }
    @Test fun `conversation history is validated and bounded`() {
        jakarta.validation.Validation.buildDefaultValidatorFactory().use { factory ->
            val validator = factory.validator
            assertThat(validator.validate(AiQuestionRequest("Follow up", history = List(7) { AiQuestionContext("Earlier question") }))).isNotEmpty()
            assertThat(validator.validate(AiQuestionRequest("Follow up", history = listOf(AiQuestionContext(" "))))).isNotEmpty()
            assertThat(validator.validate(AiQuestionRequest("Follow up", history = listOf(AiQuestionContext("x".repeat(4001)))))).isNotEmpty()
            assertThat(validator.validate(AiQuestionRequest("Follow up", history = listOf(AiQuestionContext("Earlier question"))))).isEmpty()
        }
    }
}
