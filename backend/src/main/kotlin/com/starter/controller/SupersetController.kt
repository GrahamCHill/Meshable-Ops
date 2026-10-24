package com.starter.controller

import com.starter.dto.*
import com.starter.exception.BadRequestException
import com.starter.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/superset")
class SupersetController(
    private val supersetService: SupersetService,
    private val dashboardEmbedConfigService: DashboardEmbedConfigService
) {

    data class GuestTokenRequest(
        @field:NotEmpty val dashboardIds: List<String>,
        val appOrigin: String? = null,
    )

    @PostMapping("/guest-token")
    fun guestToken(
        @AuthenticationPrincipal principal: UserDetails,
        request: HttpServletRequest,
        @Valid @RequestBody req: GuestTokenRequest
    ): Map<String, String> {
        val appOrigin = req.appOrigin?.trim()?.ifBlank { null } ?: supersetService.resolveAppOrigin(
            originHeader = request.getHeader("Origin"),
            refererHeader = request.getHeader("Referer"),
            forwardedProtoHeader = request.getHeader("X-Forwarded-Proto"),
            forwardedHostHeader = request.getHeader("X-Forwarded-Host"),
            hostHeader = request.getHeader("Host"),
        )
        val resolvedDashboardIds = dashboardEmbedConfigService.resolveGuestTokenDashboardIds(req.dashboardIds, principal.authorities.map { it.authority.removePrefix("ROLE_") }.let { if ("SUPER_ADMIN" in it) "SUPER_ADMIN" else it.firstOrNull() ?: "USER" })
        if (resolvedDashboardIds.isEmpty()) {
            throw BadRequestException("No numeric Superset dashboard id is mapped for the requested embed")
        }
        return mapOf(
            "token" to supersetService.mintGuestToken(
                dashboardIds = resolvedDashboardIds,
                userEmail = principal.username,
                username = principal.username,
                appOrigin = appOrigin,
                dashboardIdsToSync = resolvedDashboardIds,
            )
        )
    }

    @GetMapping("/dashboard-embeds")
    fun listDashboardEmbeds(@AuthenticationPrincipal principal: UserDetails) =
        dashboardEmbedConfigService.listEnabledForRole(principal.authorities.map { it.authority.removePrefix("ROLE_") }.let { if ("SUPER_ADMIN" in it) "SUPER_ADMIN" else it.firstOrNull() ?: "USER" })

    @GetMapping("/dashboard-embeds/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun listAllDashboardEmbeds(@AuthenticationPrincipal principal: UserDetails) =
        dashboardEmbedConfigService.listAll()

    @GetMapping("/dashboard-embeds/roles")
    @PreAuthorize("hasRole('ADMIN')")
    fun listDashboardEmbedRoles(@AuthenticationPrincipal principal: UserDetails) =
        dashboardEmbedConfigService.listAvailableRoles()

    @GetMapping("/dashboards/available")
    @PreAuthorize("hasRole('ADMIN')")
    fun listAvailableSupersetDashboards(@AuthenticationPrincipal principal: UserDetails) =
        supersetService.listDashboardOptions()

    @PostMapping("/dashboard-embeds")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDashboardEmbed(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: DashboardEmbedConfigRequest
    ) = dashboardEmbedConfigService.create(req)

    @PutMapping("/dashboard-embeds/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateDashboardEmbed(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: DashboardEmbedConfigRequest
    ) = dashboardEmbedConfigService.update(id, req)

    @DeleteMapping("/dashboard-embeds/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDashboardEmbed(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID
    ) = dashboardEmbedConfigService.delete(id)
}
