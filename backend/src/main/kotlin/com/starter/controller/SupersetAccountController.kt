package com.starter.controller

import com.starter.service.SupersetAccountService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class SupersetPasswordRequest(
    @field:NotBlank @field:Size(min = 14, max = 128) val password: String,
) {
    override fun toString() = "SupersetPasswordRequest([redacted])"
}

@RestController
@RequestMapping("/api/superset/account")
@PreAuthorize("hasRole('ADMIN')")
class SupersetAccountController(private val accounts: SupersetAccountService) {
    @PostMapping("/password")
    fun setPassword(@Valid @RequestBody request: SupersetPasswordRequest) = accounts.setPassword(request.password)
}
