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
    private val access: com.starter.access.AccessControlService? = null,
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
        val actor = actingAdmin(actingEmail)
        if (role !in com.starter.access.Roles.all || actor.role != "SUPER_ADMIN" && com.starter.access.Roles.admin(role)) {
            throw com.starter.exception.ForbiddenException("Only a super admin can assign administrator roles")
        }
        if (role == "EXTERNAL" && req.canUploadDocuments) throw com.starter.exception.BadRequestException("External members have read-only storage access")
        if (actor.role != "SUPER_ADMIN" && req.canUploadDocuments && !actor.canUploadDocuments) throw com.starter.exception.ForbiddenException("You cannot grant upload rights beyond your own permissions")

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
        access?.inheritForCreated(actor, created)
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
        userRepository.lockSuperAdmins()
        val user = userRepository.lockedById(userId)
            .orElseThrow { NotFoundException("User not found") }
        requireManage(actingEmail, user)

        if (user.email.equals(actingEmail.trim(), ignoreCase = true)) {
            throw ConflictException("You cannot deactivate your own account")
        }

        if (!user.active) {
            return toSummary(user)
        }

        user.active = false
        user.sessionVersion++
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
        val user = userRepository.lockedById(userId)
            .orElseThrow { NotFoundException("User not found") }
        requireManage(actingEmail, user)
        if (user.role == "EXTERNAL" && req.canUploadDocuments) throw com.starter.exception.BadRequestException("External members have read-only storage access")
        val actor = actingAdmin(actingEmail)
        if (actor.role != "SUPER_ADMIN" && req.canUploadDocuments && !actor.canUploadDocuments) throw com.starter.exception.ForbiddenException("You cannot grant upload rights beyond your own permissions")
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

    @Transactional
    fun updateRole(userId: UUID, role: String, actingEmail: String): UserSummaryResponse {
        userRepository.lockSuperAdmins()
        if (actingAdmin(actingEmail).role != "SUPER_ADMIN") throw com.starter.exception.ForbiddenException("Only a super admin can change roles")
        if (role !in com.starter.access.Roles.all) throw com.starter.exception.BadRequestException("Invalid role")
        val user = userRepository.lockedById(userId).orElseThrow { NotFoundException("User not found") }
        if (user.email.equals(actingEmail, true) && role != user.role) throw ConflictException("You cannot change your own super admin role")
        if (user.role == "SUPER_ADMIN" && role != user.role && userRepository.findAll().count { it.active && it.role == "SUPER_ADMIN" } <= 1) throw ConflictException("Keep at least one active super admin")
        if (role != user.role) {
            user.role = role; user.sessionVersion++
            if (role == "EXTERNAL") user.canUploadDocuments = false
            userRepository.saveAndFlush(user)
            auditLogService.record(actingEmail = actingEmail, action = "USER_ROLE_CHANGED", resourceType = "USER", resourceId = user.id.toString())
        }
        return toSummary(user)
    }

    private fun actingAdmin(email: String): User = userRepository.findByEmail(email).orElse(null)?.takeIf {
        it.active && com.starter.access.Roles.admin(it.role)
    } ?: throw com.starter.exception.ForbiddenException("Administrator access required")

    private fun requireManage(email: String, target: User) {
        val actor = actingAdmin(email)
        if (actor.role != "SUPER_ADMIN" && com.starter.access.Roles.admin(target.role)) throw com.starter.exception.ForbiddenException("Only a super admin can manage administrator accounts")
        if (access?.canManage(actor, target) == false) throw com.starter.exception.ForbiddenException("This account has permissions outside your management scope")
        if (target.role == "SUPER_ADMIN" && target.active && userRepository.findAll().count { it.active && it.role == "SUPER_ADMIN" } <= 1 && target.email != email) throw ConflictException("Keep at least one active super admin")
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
