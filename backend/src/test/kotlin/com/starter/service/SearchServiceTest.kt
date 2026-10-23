package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.ai.*
import com.starter.dto.VectorSearchRequest
import com.starter.exception.ForbiddenException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate

class SearchServiceTest {
    private val vectors = mock(VectorService::class.java)
    private val keywords = mock(KeywordSearchService::class.java)
    private val settings = mock(AiSettingsService::class.java)
    private val endpoint = AiEndpoint(AiProvider.LOCAL, "", LocalTextVectors.MODEL)
    private val config = ActiveAiConfiguration(false, false, endpoint, endpoint)
    private val point = VectorSearchPoint("document", 0.7f, mapOf("title" to "Contract", "entityType" to "document"))
    private val service = SearchService(vectors, keywords, settings)

    @Test fun `search works when AI answers are disabled`() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("contract", 10, null, null)).thenReturn(listOf(point))
        val result = service.search(VectorSearchRequest("contract"))
        assertThat(result.mode).isEqualTo("VECTOR")
        assertThat(result.results.single().pointId).isEqualTo("document")
        verifyNoInteractions(keywords)
    }
    @Test fun `an unavailable embedding provider falls back to access checked keywords`() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("contract", 10, null, null)).thenThrow(AiUnavailableException("Provider offline"))
        `when`(keywords.search("contract", 10, false)).thenReturn(listOf(point))
        val result = service.search(VectorSearchRequest("contract"))
        assertThat(result.mode).isEqualTo("KEYWORD")
        assertThat(result.notice).contains("unavailable")
        assertThat(result.results).hasSize(1)
    }
    @Test fun `an index awaiting reindexing still supports keyword search`() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("contract", 10, null, null)).thenReturn(emptyList())
        `when`(keywords.search("contract", 10, false)).thenReturn(listOf(point))
        assertThat(service.search(VectorSearchRequest("contract")).mode).isEqualTo("KEYWORD")
    }
    @Test fun `disabled search is enforced before vector or keyword queries`() {
        `when`(settings.current()).thenReturn(config.copy(searchEnabled = false))
        assertThatThrownBy { service.search(VectorSearchRequest("contract")) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(vectors, keywords)
    }
    @Test fun `keyword fallback checks live visibility and never searches MongoDB events or audit logs`() {
        val statements = mutableListOf<String>()
        val arguments = mutableListOf<Any?>()
        val jdbc = mock(JdbcTemplate::class.java) { call ->
            if (call.method.name == "query") {
                statements += call.arguments[0] as String
                arguments.addAll(call.arguments.drop(2))
                emptyList<VectorSearchPoint>()
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
        KeywordSearchService(jdbc, jacksonObjectMapper()).search("contract'; DROP TABLE users;--", 10, false)
        val sql = statements.single()
        assertThat(Regex("i.accessible = TRUE AND \\(sc.id IS NULL OR sc.enabled = TRUE\\)").findAll(sql).count()).isEqualTo(3)
        assertThat(sql).doesNotContain("DROP TABLE", "activity_logs", "events")
        assertThat(arguments).contains(false, 10)
    }
}
