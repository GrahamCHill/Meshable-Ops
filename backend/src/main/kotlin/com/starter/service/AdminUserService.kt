package com.starter.service

import com.starter.dto.AdminCreateUserRequest
import com.starter.dto.UserSummaryResponse
import com.starter.dto.UpdateUploadPermissionRequest
import com.starter.exception.ConflictException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditLogService: AuditLogService,
) {

    @Transactional(readOnly = true)
    fun listUsers(): List<UserSummaryResponse> =
        userRepository.findAllByOrderByRoleAscCreatedAtAsc()
            .map(::toSummary)

    @Transactional
    fun createUser(req: AdminCreateUserRequest, actingEmail: String): UserSummaryResponse {
        val email = req.email.trim().lowercase()
        val username = req.username.trim()
        val name = req.name?.trim()?.ifBlank { null }
        val contactNumber = req.contactNumber?.trim()?.ifBlank { null }
        val role = req.role.trim().uppercase()

        if (userRepository.existsByEmail(email)) {
            throw ConflictException("Email already in use")
        }
        if (userRepository.existsByUsername(username)) {
            throw ConflictException("Username already taken")
        }

        val created = userRepository.save(
            User(
                email = email,
                username = username,
                name = name,
                contactNumber = contactNumber,
                password = passwordEncoder.encode(req.password),
                role = role,
                canUploadDocuments = req.canUploadDocuments,
            )
        )
        auditLogService.record(
            actingEmail = actingEmail,
            action = "USER_CREATED",
            resourceType = "USER",
            resourceId = created.id.toString(),
        )
        return toSummary(created)
    }

    @Transactional
    fun deactivateUser(userId: UUID, actingEmail: String): UserSummaryResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }

        if (user.email.equals(actingEmail.trim(), ignoreCase = true)) {
            throw ConflictException("You cannot deactivate your own account")
        }

        if (!user.active) {
            return toSummary(user)
        }

        user.active = false
        val saved = userRepository.save(user)
        auditLogService.record(
            actingEmail = actingEmail,
            action = "USER_DEACTIVATED",
            resourceType = "USER",
            resourceId = saved.id.toString(),
        )
        return toSummary(saved)
    }

    @Transactional
    fun updateUploadPermission(userId: UUID, req: UpdateUploadPermissionRequest, actingEmail: String): UserSummaryResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }
        user.canUploadDocuments = req.canUploadDocuments
        val saved = userRepository.save(user)
        auditLogService.record(
            actingEmail = actingEmail,
            action = if (req.canUploadDocuments) "DOCUMENT_UPLOAD_GRANTED" else "DOCUMENT_UPLOAD_REVOKED",
            resourceType = "USER",
            resourceId = saved.id.toString(),
        )
        return toSummary(saved)
    }

    private fun toSummary(user: User) = UserSummaryResponse(
        id = user.id.toString(),
        email = user.email,
        username = user.username,
        name = user.name,
        contactNumber = user.contactNumber,
        role = user.role,
        canUploadDocuments = user.canUploadDocuments,
        active = user.active,
        createdAt = user.createdAt.toString(),
        updatedAt = user.updatedAt.toString(),
    )
}
