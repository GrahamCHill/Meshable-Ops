package com.starter.service

import com.starter.dto.AdminCreateUserRequest
import com.starter.dto.UserSummaryResponse
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
                password = passwordEncoder.encode(req.password),
                role = role,
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

    private fun toSummary(user: User) = UserSummaryResponse(
        id = user.id.toString(),
        email = user.email,
        username = user.username,
        role = user.role,
        active = user.active,
        createdAt = user.createdAt.toString(),
        updatedAt = user.updatedAt.toString(),
    )
}
