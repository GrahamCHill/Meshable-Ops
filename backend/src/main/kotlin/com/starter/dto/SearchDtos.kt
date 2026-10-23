package com.starter.dto

import jakarta.validation.constraints.*

data class VectorSearchRequest(
    @field:NotBlank @field:Size(max = 4000) val query: String,
    @field:Min(1) @field:Max(50) val limit: Int = 10
)

data class VectorSearchResult(
    val pointId: String,
    val score: Float,
    val payload: Map<String, String>
)

data class SearchResponse(val results: List<VectorSearchResult>, val mode: String, val notice: String? = null)
