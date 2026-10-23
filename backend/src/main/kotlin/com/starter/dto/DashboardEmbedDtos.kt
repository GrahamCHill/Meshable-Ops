package com.starter.dto

import jakarta.validation.constraints.*

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
