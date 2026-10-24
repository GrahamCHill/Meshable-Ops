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
import com.starter.repository.postgres.ItemRepository

class SearchServiceTest {
    private val vectors = mock(VectorService::class.java)
    private val keywords = mock(KeywordSearchService::class.java)
    private val settings = mock(AiSettingsService::class.java)
    private val endpoint = AiEndpoint(AiProvider.LOCAL, "", LocalTextVectors.MODEL)
    private val config = ActiveAiConfiguration(false, false, endpoint, endpoint)
    private val point = VectorSearchPoint("document", 0.7f, mapOf("title" to "Contract", "entityType" to "document"))
    private val service = SearchService(vectors, keywords, settings, SearchScopeResolver(mock(ItemRepository::class.java)))

    @Test fun `global search merges plugin results using the authenticated identity`() {
        val plugins = mock(com.starter.plugins.PluginSearchService::class.java)
        val combined = SearchService(vectors, keywords, settings, SearchScopeResolver(mock(ItemRepository::class.java)), plugins)
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("contract", 10, null, null)).thenReturn(listOf(point))
        `when`(plugins.search("member@example.test", "contract", 10)).thenReturn(listOf(VectorSearchPoint("plugin:kanban:1", 0.9f, mapOf("entityType" to "plugin"))))
        val result = combined.search(VectorSearchRequest("contract"), "member@example.test")
        assertThat(result.mode).isEqualTo("MIXED")
        assertThat(result.results.map { it.pointId }).containsExactly("plugin:kanban:1", "document")
    }

    @Test fun `folder scoped storage search never invokes plugin providers`() {
        val plugins = mock(com.starter.plugins.PluginSearchService::class.java)
        val items = mock(ItemRepository::class.java)
        val folder = com.starter.model.postgres.Item()
        `when`(items.findById(folder.id)).thenReturn(java.util.Optional.of(folder))
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchTextScoped("contract", 10, SearchScope(folder.id))).thenReturn(listOf(point))
        val scoped = SearchService(vectors, keywords, settings, SearchScopeResolver(items), plugins)
        assertThat(scoped.search(VectorSearchRequest("contract", folderId = folder.id), "member@example.test").results).hasSize(1)
        verifyNoInteractions(plugins)
    }

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
        `when`(keywords.search("contract", 10, false, SearchScope())).thenReturn(listOf(point))
        val result = service.search(VectorSearchRequest("contract"))
        assertThat(result.mode).isEqualTo("KEYWORD")
        assertThat(result.notice).contains("unavailable")
        assertThat(result.results).hasSize(1)
    }
    @Test fun `an index awaiting reindexing still supports keyword search`() {
        `when`(settings.current()).thenReturn(config)
        `when`(vectors.searchText("contract", 10, null, null)).thenReturn(emptyList())
        `when`(keywords.search("contract", 10, false, SearchScope())).thenReturn(listOf(point))
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

    @Test fun `a revoked or invalid folder scope fails before embedding`() {
        val items = mock(ItemRepository::class.java)
        val folder = com.starter.model.postgres.Item(accessible = false)
        `when`(items.findById(folder.id)).thenReturn(java.util.Optional.of(folder))
        val scopedService = SearchService(vectors, keywords, settings, SearchScopeResolver(items))
        `when`(settings.current()).thenReturn(config)
        assertThatThrownBy { scopedService.search(VectorSearchRequest("contract", folderId = folder.id)) }.isInstanceOf(com.starter.exception.NotFoundException::class.java)
        folder.accessible = true
        assertThatThrownBy { scopedService.search(VectorSearchRequest("contract", folderId = folder.id, path = "../private")) }.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        assertThatThrownBy { scopedService.search(VectorSearchRequest("contract", path = "legal/")) }.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        verifyNoInteractions(vectors, keywords)
    }

    @Test fun `keyword and vector queries constrain the folder and use literal path prefixes`() {
        val statements = mutableListOf<String>()
        val arguments = mutableListOf<Any?>()
        val jdbc = mock(JdbcTemplate::class.java) { call ->
            if (call.method.name == "query") {
                statements += call.arguments[0] as String; arguments.addAll(call.arguments.drop(2)); emptyList<VectorSearchPoint>()
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
        val scope = SearchScope(java.util.UUID.randomUUID(), "legal_%/2026/")
        KeywordSearchService(jdbc, jacksonObjectMapper()).search("contract", 10, false, scope)
        VectorService(jdbc, jacksonObjectMapper(), mock(EmbeddingService::class.java)).search(listOf(1f), 10, model = "test", scope = scope)
        statements.forEach { sql ->
            assertThat(Regex("i.id = s.folder_id").findAll(sql).count()).isEqualTo(3)
            assertThat(Regex("left\\(d.storage_path, length\\(s.path_prefix\\)\\) = s.path_prefix").findAll(sql).count()).isEqualTo(2)
            assertThat(sql).doesNotContain("legal_%", " LIKE ")
        }
        assertThat(arguments).contains(scope.folderId, scope.pathPrefix)
    }
}
