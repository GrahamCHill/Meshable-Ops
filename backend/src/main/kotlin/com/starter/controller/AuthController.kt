package com.starter.controller

import com.starter.dto.*
import com.starter.exception.ForbiddenException
import com.starter.service.*
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest, response: HttpServletResponse): AuthResponse {
        throw ForbiddenException("Registration is invite-only")
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: AuthRequest, response: HttpServletResponse) = authService.login(req, response)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(response: HttpServletResponse) = authService.clearAuthCookie(response)

    @PostMapping("/session-cookie")
    fun refreshSessionCookie(
        @AuthenticationPrincipal principal: UserDetails?,
        response: HttpServletResponse
    ): AuthResponse {
        if (principal == null) {
            throw ForbiddenException("Authentication required")
        }
        return authService.refreshAuthCookie(principal.username, response)
    }

    @GetMapping("/superset-admin-access")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun supersetAdminAccess(@AuthenticationPrincipal principal: UserDetails) = Unit
}
