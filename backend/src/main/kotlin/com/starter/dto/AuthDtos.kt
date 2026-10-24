package com.starter.dto

import jakarta.validation.constraints.*
import com.starter.security.PasswordBytes

data class AuthRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:PasswordBytes val password: String
) { override fun toString() = "AuthRequest(email=$email)" }

data class RegisterRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val username: String,
    @field:NotBlank @field:Size(min = 8, max = 72) @field:PasswordBytes val password: String
) { override fun toString() = "RegisterRequest(email=$email, username=$username)" }

interface LoginResponse

data class AuthResponse(
    val token: String,
    val id: String,
    val email: String,
    val username: String,
    val name: String?,
    val contactNumber: String?,
    val role: String,
    val canUploadDocuments: Boolean,
    val suggestMfaSetup: Boolean = false,
) : LoginResponse
