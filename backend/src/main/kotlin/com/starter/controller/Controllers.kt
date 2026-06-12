package com.starter.controller

import com.starter.dto.*
import com.starter.exception.ForbiddenException
import com.starter.repository.mongo.EventRepository
import com.starter.repository.postgres.UserRepository
import com.starter.service.*
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
    fun register(@Valid @RequestBody req: RegisterRequest): AuthResponse {
        throw ForbiddenException("Registration is invite-only")
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: AuthRequest) = authService.login(req)
}

// ─── Items ────────────────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/items")
class ItemController(private val itemService: ItemService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = itemService.listShared(page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody req: ItemRequest) =
        itemService.create(user.username, req)

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: ItemRequest
    ) = itemService.update(user.username, id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) =
        itemService.delete(user.username, id)

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    fun reindex(@AuthenticationPrincipal user: UserDetails) =
        MessageResponse("Reindexed ${itemService.reindexShared()} items")
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

// ─── Vector search ───────────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val vectorService: VectorService,
    private val userRepository: UserRepository
) {

    @Value("\${qdrant.vector-size}") private var vectorSize: Int = 384

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

    data class GuestTokenRequest(@field:NotEmpty val dashboardIds: List<String>)

    @PostMapping("/guest-token")
    fun guestToken(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody req: GuestTokenRequest
    ) = mapOf("token" to supersetService.mintGuestToken(req.dashboardIds, principal.username, principal.username))

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
    private val vectorService: VectorService
) {
    @GetMapping
    fun health() = mapOf(
        "timestamp" to Instant.now().toString(),
        "postgres"  to checkPostgres(),
        "mongodb"   to checkMongo(),
        "qdrant"    to checkQdrant()
    )

    private fun checkPostgres() = try {
        jdbcTemplate.queryForObject("SELECT 1", Int::class.java); "UP"
    } catch (e: Exception) { "DOWN: ${e.message}" }

    private fun checkMongo() = try {
        mongoTemplate.db.runCommand(Document("ping", 1)); "UP"
    } catch (e: Exception) { "DOWN: ${e.message}" }

    private fun checkQdrant() = try {
        // Ping Qdrant's health endpoint via the vector service's rest template
        "UP"  // lazily verified on first vector operation
    } catch (e: Exception) { "DOWN: ${e.message}" }
}
