package com.starter.service

import com.starter.ai.AiSettingsService
import com.starter.ai.AiUnavailableException
import com.starter.dto.SearchResponse
import com.starter.dto.VectorSearchRequest
import com.starter.dto.VectorSearchResult
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import org.springframework.stereotype.Service

@Service
class SearchService(private val vectors: VectorService, private val keywords: KeywordSearchService, private val settings: AiSettingsService, private val scopes: SearchScopeResolver) {
    fun search(request: VectorSearchRequest): SearchResponse {
        if (!settings.current().searchEnabled) throw ForbiddenException("Search is disabled by an administrator")
        val scope = scopes.resolve(request)
        return try {
            val results = if (scope.folderId == null) vectors.searchText(request.query, request.limit)
                else vectors.searchTextScoped(request.query, request.limit, scope)
            if (results.isEmpty()) {
                val matches = keywords.search(request.query, request.limit, settings.current().indexContent, scope)
                response(matches, if (matches.isEmpty()) "VECTOR" else "KEYWORD", if (matches.isEmpty()) null else "Showing keyword matches while the vector index is empty or awaiting reindexing.")
            } else response(results, "VECTOR")
        } catch (_: AiUnavailableException) { fallback(request, scope) }
          catch (_: BadRequestException) { fallback(request, scope) }
    }

    private fun fallback(request: VectorSearchRequest, scope: SearchScope): SearchResponse = response(
        keywords.search(request.query, request.limit, settings.current().indexContent, scope), "KEYWORD",
        "The configured vector service is unavailable. Showing keyword matches instead. Storage and search remain available.",
    )
    private fun response(points: List<VectorSearchPoint>, mode: String, notice: String? = null) =
        SearchResponse(points.map { VectorSearchResult(it.id, it.score, it.payload) }, mode, notice)
}
