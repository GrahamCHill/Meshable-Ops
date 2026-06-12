package com.starter.service

import com.starter.dto.AuthRequest
import com.starter.dto.AuthResponse
import com.starter.dto.RegisterRequest
import com.starter.exception.ConflictException
import com.starter.model.postgres.User
import jakarta.servlet.http.HttpServletResponse
import com.starter.repository.postgres.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
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
    @Value("\${jwt.expiry-ms}") private var expiryMs: Long = 86_400_000L

    @Transactional
    fun register(req: RegisterRequest, response: HttpServletResponse): AuthResponse {
        if (userRepository.existsByEmail(req.email)) throw ConflictException("Email already in use")
        if (userRepository.existsByUsername(req.username)) throw ConflictException("Username already taken")

        val user = userRepository.save(
            User(email = req.email, username = req.username,
                 password = passwordEncoder.encode(req.password), role = "USER")
        )
        val ud = userDetailsService.loadUserByUsername(user.email)
        val token = jwtService.generateToken(ud, mapOf("role" to user.role, "id" to user.id.toString()))
        writeAuthCookie(response, token, expiryMs)
        return AuthResponse(token, user.id.toString(), user.email, user.username, user.role)
    }

    fun login(req: AuthRequest, response: HttpServletResponse): AuthResponse {
        authManager.authenticate(UsernamePasswordAuthenticationToken(req.email, req.password))
        val user = userRepository.findByEmail(req.email)
            .orElseThrow { UsernameNotFoundException("User not found") }
        val ud = userDetailsService.loadUserByUsername(user.email)
        val token = jwtService.generateToken(ud, mapOf("role" to user.role, "id" to user.id.toString()))
        writeAuthCookie(response, token, expiryMs)
        return AuthResponse(token, user.id.toString(), user.email, user.username, user.role)
    }

    fun refreshAuthCookie(email: String, response: HttpServletResponse) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found") }
        val ud = userDetailsService.loadUserByUsername(user.email)
        val token = jwtService.generateToken(ud, mapOf("role" to user.role, "id" to user.id.toString()))
        writeAuthCookie(response, token, expiryMs)
    }

    fun clearAuthCookie(response: HttpServletResponse) {
        writeAuthCookie(response, "", 0)
    }

    private fun writeAuthCookie(response: HttpServletResponse, token: String, maxAgeMs: Long) {
        val cookie = ResponseCookie.from("app_auth_token", token)
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAgeMs / 1000)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }
}
