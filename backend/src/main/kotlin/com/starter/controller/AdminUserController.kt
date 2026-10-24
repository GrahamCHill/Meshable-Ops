package com.starter.controller

import com.starter.dto.*
import com.starter.service.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(private val adminUserService: AdminUserService) {

    @PatchMapping("/{id}/role")
    fun role(@PathVariable id: java.util.UUID, @Valid @RequestBody request: com.starter.dto.UpdateUserRoleRequest,
        @AuthenticationPrincipal principal: UserDetails) = adminUserService.updateRole(id, request.role, principal.username)

    @GetMapping
    fun listUsers() = adminUserService.listUsers()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(
        @Valid @RequestBody req: AdminCreateUserRequest,
        @AuthenticationPrincipal principal: UserDetails
    ) = adminUserService.createUser(req, principal.username)

    @DeleteMapping("/{id}")
    fun deactivateUser(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserDetails
    ) = adminUserService.deactivateUser(id, principal.username)

    @PatchMapping("/{id}/upload-permission")
    fun updateUploadPermission(
        @PathVariable id: UUID,
        @RequestBody req: UpdateUploadPermissionRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ) = adminUserService.updateUploadPermission(id, req, principal.username)
}
