package com.starter.dto

import jakarta.validation.constraints.*

// ─── Auth ─────────────────────────────────────────────────────────────────────

data class RegisterRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val username: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String
)

data class AuthRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String
)

data class AuthResponse(
    val token: String,
    val id: String,
    val email: String,
    val username: String,
    val role: String
)

data class AdminCreateUserRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val username: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank @field:Pattern(regexp = "ADMIN|USER") val role: String = "USER"
)

data class UserSummaryResponse(
    val id: String,
    val email: String,
    val username: String,
    val role: String,
    val active: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)

// ─── Items ────────────────────────────────────────────────────────────────────

data class ItemRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

data class ItemResponse(
    val id: String,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val createdAt: String?,
    val updatedAt: String?
)

// ─── Vector search ───────────────────────────────────────────────────────────

data class VectorSearchRequest(
    @field:NotBlank val query: String,
    @field:Min(1) @field:Max(50) val limit: Int = 10
)

data class VectorSearchResult(
    val pointId: String,
    val score: Float,
    val payload: Map<String, String>
)

// ─── Events ──────────────────────────────────────────────────────────────────

data class EventResponse(
    val id: String?,
    val type: String,
    val payload: Map<String, Any>,
    val createdAt: String?
)

// Dashboard embeds

data class DashboardEmbedConfigRequest(
    @field:NotBlank @field:Size(max = 255) val label: String,
    val description: String? = null,
    @field:NotBlank @field:Size(max = 255) val dashboardId: String,
    @field:Size(max = 255) val guestTokenResourceId: String? = null,
    @field:Min(0) val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val allowedRoles: List<@NotBlank String> = emptyList()
)

data class DashboardEmbedConfigResponse(
    val id: String,
    val label: String,
    val description: String?,
    val dashboardId: String,
    val guestTokenResourceId: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val allowedRoles: List<String>,
    val createdAt: String?,
    val updatedAt: String?
)

// ─── Generic ─────────────────────────────────────────────────────────────────

data class MessageResponse(val message: String)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
