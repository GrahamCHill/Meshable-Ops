package com.starter.service

import com.starter.dto.AuditLogResponse
import com.starter.dto.PageResponse
import com.starter.model.mongo.ActivityLog
import com.starter.repository.mongo.ActivityLogRepository
import com.starter.repository.postgres.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuditLogService(
    private val activityLogRepository: ActivityLogRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<AuditLogResponse> {
        val result = activityLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size))
        val usersById = userRepository.findAllById(
            result.content.mapNotNull { parseUserId(it.userId) }
        ).associateBy { it.id.toString() }

        return PageResponse(
            content = result.content.map { entry -> toResponse(entry, usersById[entry.userId]) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    fun record(
        actingEmail: String?,
        action: String,
        resourceType: String? = null,
        resourceId: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
    ) {
        val normalizedEmail = actingEmail?.trim()?.lowercase()
        val userId = normalizedEmail
            ?.let { email -> userRepository.findByEmail(email).orElse(null)?.id?.toString() }
            ?: "system"

        activityLogRepository.save(
            ActivityLog(
                userId = userId,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                timestamp = Instant.now(),
            )
        )
    }

    private fun toResponse(entry: ActivityLog, user: com.starter.model.postgres.User?) = AuditLogResponse(
        id = entry.id,
        userId = entry.userId,
        userEmail = user?.email ?: fallbackAuditEmail(entry.userId),
        userName = user?.name,
        userContactNumber = user?.contactNumber,
        action = entry.action,
        resourceType = entry.resourceType,
        resourceId = entry.resourceId,
        ipAddress = entry.ipAddress,
        userAgent = entry.userAgent,
        timestamp = entry.timestamp.toString(),
    )

    private fun parseUserId(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

    private fun fallbackAuditEmail(userId: String): String? = when {
        userId == "system" -> "system"
        userId.contains("@") -> userId
        else -> null
    }
}
