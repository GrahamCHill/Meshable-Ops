package com.starter.dto

import jakarta.validation.constraints.*
import com.starter.security.PasswordBytes

data class AdminCreateUserRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val username: String,
    @field:Size(max = 120) val name: String? = null,
    @field:Size(max = 32) val contactNumber: String? = null,
    @field:NotBlank @field:Size(min = 8, max = 72) @field:PasswordBytes val password: String,
    @field:NotBlank @field:Pattern(regexp = "SUPER_ADMIN|ADMIN|BUSINESS_MANAGER|USER|EXTERNAL") val role: String = "USER",
    val canUploadDocuments: Boolean = false,
) { override fun toString() = "AdminCreateUserRequest(email=$email, role=$role)" }

data class UpdateUploadPermissionRequest(val canUploadDocuments: Boolean)

data class UserSummaryResponse(
    val id: String,
    val email: String,
    val username: String,
    val name: String?,
    val contactNumber: String?,
    val role: String,
    val canUploadDocuments: Boolean,
    val active: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)

data class UpdateUserRoleRequest(@field:Pattern(regexp = "SUPER_ADMIN|ADMIN|BUSINESS_MANAGER|USER|EXTERNAL") val role: String)
