package com.starter.service

import com.starter.dto.AdminCreateUserRequest
import com.starter.dto.UserSummaryResponse
import com.starter.exception.ConflictException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional(readOnly = true)
    fun listUsers(): List<UserSummaryResponse> =
        userRepository.findAllByOrderByRoleAscCreatedAtAsc()
            .map(::toSummary)

    @Transactional
    fun createUser(req: AdminCreateUserRequest): UserSummaryResponse {
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
        return toSummary(created)
    }

    private fun toSummary(user: User) = UserSummaryResponse(
        id = user.id.toString(),
        email = user.email,
        username = user.username,
        role = user.role,
        createdAt = user.createdAt.toString(),
        updatedAt = user.updatedAt.toString(),
    )
}
