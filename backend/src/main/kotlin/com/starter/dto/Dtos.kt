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
    val name: String?,
    val contactNumber: String?,
    val role: String
)

data class AdminCreateUserRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val username: String,
    @field:Size(max = 120) val name: String? = null,
    @field:Size(max = 32) val contactNumber: String? = null,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank @field:Pattern(regexp = "ADMIN|USER") val role: String = "USER"
)

data class UserSummaryResponse(
    val id: String,
    val email: String,
    val username: String,
    val name: String?,
    val contactNumber: String?,
    val role: String,
    val active: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)

data class AdminSettingsRequest(
    @field:Pattern(regexp = "NONE|S3|AZURE_BLOB") val storageProvider: String = "NONE",
    @field:Size(max = 255) val storageBucket: String? = null,
    @field:Size(max = 255) val storageRegion: String? = null,
    @field:Size(max = 512) val storageEndpoint: String? = null,
    @field:Size(max = 255) val storageAccessKey: String? = null,
    @field:Size(max = 512) val storageSecretKey: String? = null,
    @field:NotBlank @field:Size(max = 512) val storageRootPath: String = "folders",
    val vectorReindexEnabled: Boolean = false,
    @field:Min(15) @field:Max(10080) val vectorReindexIntervalMinutes: Int = 1440
)

data class AdminSettingsResponse(
    val id: String,
    val storageProvider: String,
    val storageBucket: String?,
    val storageRegion: String?,
    val storageEndpoint: String?,
    val storageAccessKey: String?,
    val storageSecretKey: String?,
    val storageRootPath: String,
    val vectorReindexEnabled: Boolean,
    val vectorReindexIntervalMinutes: Int,
    val lastVectorReindexAt: String?,
    val lastVectorReindexStatus: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class AuditLogResponse(
    val id: String?,
    val userId: String,
    val userEmail: String?,
    val userName: String?,
    val userContactNumber: String?,
    val action: String,
    val resourceType: String?,
    val resourceId: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val timestamp: String?
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
    val storagePrefix: String,
    val storageProvider: String,
    val documentCount: Int,
    val createdAt: String?,
    val updatedAt: String?
)

data class StorageEntryResponse(
    val name: String,
    val path: String,
    val type: String,
    val size: Long?,
    val contentType: String?,
    val lastModified: String?,
    val documentId: String? = null,
    val vectorStatus: String? = null,
)

data class StorageContentsResponse(
    val folderId: String,
    val rootPrefix: String,
    val path: String,
    val storageProvider: String,
    val entries: List<StorageEntryResponse>
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
    @field:Min(1) val supersetDashboardId: Int,
    @field:Min(0) val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val allowedRoles: List<@NotBlank String> = emptyList()
)

data class SupersetDashboardOptionResponse(
    val id: Int,
    val title: String,
    val published: Boolean,
    val embeddingEnabled: Boolean,
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
