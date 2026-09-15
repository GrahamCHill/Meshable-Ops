package com.starter.controller

import com.starter.dto.*
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.repository.mongo.EventRepository
import com.starter.repository.postgres.UserRepository
import com.starter.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.data.mongodb.core.MongoTemplate
import org.bson.Document
import java.time.Instant
import java.util.UUID

// ─── Auth ─────────────────────────────────────────────────────────────────────

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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun refreshSessionCookie(
        @AuthenticationPrincipal principal: UserDetails?,
        response: HttpServletResponse
    ) {
        if (principal == null) {
            throw ForbiddenException("Authentication required")
        }
        authService.refreshAuthCookie(principal.username, response)
    }

    @GetMapping("/superset-admin-access")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun supersetAdminAccess(@AuthenticationPrincipal principal: UserDetails) = Unit
}

// ─── Items ────────────────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(private val adminUserService: AdminUserService) {

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
}

@RestController
@RequestMapping("/api/items")
class ItemController(
    private val itemService: ItemService,
    private val adminSettingsService: AdminSettingsService,
    private val auditLogService: AuditLogService,
) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = itemService.listShared(page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody req: ItemRequest) =
        itemService.create(user.username, req)

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: ItemRequest
    ) = itemService.update(user.username, id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) =
        itemService.delete(user.username, id)

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    fun reindex(
        @AuthenticationPrincipal user: UserDetails,
        request: HttpServletRequest
    ): MessageResponse {
        val count = itemService.reindexShared()
        adminSettingsService.recordReindexRun(count, "manual")
        auditLogService.record(
            actingEmail = user.username,
            action = "VECTOR_REINDEX_MANUAL",
            resourceType = "FOLDER",
            resourceId = count.toString(),
            ipAddress = request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
        )
        return MessageResponse("Reindexed $count folders")
    }

    @GetMapping("/{id}/contents")
    fun contents(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "") path: String,
    ) = itemService.browse(id, path)
}

// ─── Events ──────────────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<EventResponse> {
        val p = eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return PageResponse(
            content = p.content.map { EventResponse(it.id, it.type, it.payload, it.createdAt.toString()) },
            page = p.number, size = p.size, totalElements = p.totalElements, totalPages = p.totalPages
        )
    }
}

@RestController
@RequestMapping("/api/stack")
class StackController(
    private val stackInfoService: StackInfoService,
) {
    @GetMapping
    fun stack(@AuthenticationPrincipal principal: UserDetails) = stackInfoService.getStackInfo()
}

// ─── Vector search ───────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val vectorService: VectorService,
    private val userRepository: UserRepository
) {

    @Value("\${vector.dimensions}") private var vectorSize: Int = 384

    @PostMapping
    fun search(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody req: VectorSearchRequest
    ): List<VectorSearchResult> {
        val embedding = vectorService.embedText(req.query, vectorSize)
        return vectorService.search(embedding, req.limit).map { sp ->
            VectorSearchResult(pointId = sp.id, score = sp.score, payload = sp.payload)
        }
    }
}

// ─── Superset ────────────────────────────────────────────────────────────────

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
        val resolvedDashboardIds = dashboardEmbedConfigService.resolveGuestTokenDashboardIds(req.dashboardIds)
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
        dashboardEmbedConfigService.listEnabledForRole(principal.authorities.firstOrNull()?.authority?.removePrefix("ROLE_") ?: "USER")

    @GetMapping("/dashboard-embeds/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun listAllDashboardEmbeds(@AuthenticationPrincipal principal: UserDetails) =
        dashboardEmbedConfigService.listAll()

    @GetMapping("/dashboard-embeds/roles")
    @PreAuthorize("hasRole('ADMIN')")
    fun listDashboardEmbedRoles(@AuthenticationPrincipal principal: UserDetails) =
        dashboardEmbedConfigService.listAvailableRoles()

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

// ─── Health ───────────────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val jdbcTemplate: JdbcTemplate,
    private val mongoTemplate: MongoTemplate,
) {
    @GetMapping
    fun health() = mapOf(
        "timestamp" to Instant.now().toString(),
        "postgres"  to checkPostgres(),
        "mongodb"   to checkMongo(),
        "pgvector"  to checkPgvector()
    )

    private fun checkPostgres() = try {
        jdbcTemplate.queryForObject("SELECT 1", Int::class.java); "UP"
    } catch (e: Exception) { "DOWN: ${e.message}" }

    private fun checkMongo() = try {
        mongoTemplate.db.runCommand(Document("ping", 1)); "UP"
    } catch (e: Exception) { "DOWN: ${e.message}" }

    private fun checkPgvector() = try {
        val version = jdbcTemplate.queryForObject(
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
            String::class.java,
        )
        "UP ($version)"
    } catch (e: Exception) { "DOWN: ${e.message}" }
}
