package com.starter.service

import com.starter.dto.AuthRequest
import com.starter.dto.AuthResponse
import com.starter.dto.RegisterRequest
import com.starter.exception.ConflictException
import com.starter.model.postgres.User
import com.starter.repository.postgres.UserRepository
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authManager: AuthenticationManager,
    private val userDetailsService: UserDetailsServiceImpl
) {
    @Transactional
    fun register(req: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(req.email)) throw ConflictException("Email already in use")
        if (userRepository.existsByUsername(req.username)) throw ConflictException("Username already taken")

        val user = userRepository.save(
            User(email = req.email, username = req.username,
                 password = passwordEncoder.encode(req.password), role = "USER")
        )
        val ud = userDetailsService.loadUserByUsername(user.email)
        val token = jwtService.generateToken(ud, mapOf("role" to user.role, "id" to user.id.toString()))
        return AuthResponse(token, user.id.toString(), user.email, user.username, user.role)
    }

    fun login(req: AuthRequest): AuthResponse {
        authManager.authenticate(UsernamePasswordAuthenticationToken(req.email, req.password))
        val user = userRepository.findByEmail(req.email)
            .orElseThrow { UsernameNotFoundException("User not found") }
        val ud = userDetailsService.loadUserByUsername(user.email)
        val token = jwtService.generateToken(ud, mapOf("role" to user.role, "id" to user.id.toString()))
        return AuthResponse(token, user.id.toString(), user.email, user.username, user.role)
    }
}
