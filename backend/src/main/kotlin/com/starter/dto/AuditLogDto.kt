package com.starter.dto

import jakarta.validation.constraints.*

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
