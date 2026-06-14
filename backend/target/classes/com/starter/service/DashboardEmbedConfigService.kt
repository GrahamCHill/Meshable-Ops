package com.starter.service

import com.starter.dto.DashboardEmbedConfigRequest
import com.starter.dto.DashboardEmbedConfigResponse
import com.starter.exception.NotFoundException
import com.starter.model.postgres.DashboardEmbedConfig
import com.starter.repository.postgres.DashboardEmbedConfigRepository
import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DashboardEmbedConfigService(
    private val dashboardEmbedConfigRepository: DashboardEmbedConfigRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun listEnabledForRole(role: String): List<DashboardEmbedConfigResponse> =
        dashboardEmbedConfigRepository.findAllByEnabledTrueOrderBySortOrderAscCreatedAtAsc()
            .filter { config ->
                config.allowedRoles.isEmpty() || config.allowedRoles.any { it.equals(role, ignoreCase = true) }
            }
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun listAll(): List<DashboardEmbedConfigResponse> =
        dashboardEmbedConfigRepository.findAllByOrderBySortOrderAscCreatedAtAsc().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun listAvailableRoles(): List<String> = userRepository.findDistinctRoles()

    @Transactional(readOnly = true)
    fun resolveGuestTokenDashboardIds(requestedIds: List<String>): List<String> {
        if (requestedIds.isEmpty()) return emptyList()

        val configs = dashboardEmbedConfigRepository.findAll()
        return requestedIds
            .mapNotNull { requestedId ->
                val normalizedId = requestedId.trim()
                when {
                    normalizedId.isBlank() -> null
                    normalizedId.all(Char::isDigit) -> normalizedId
                    else -> configs
                        .asSequence()
                        .filter { config ->
                            config.dashboardId.equals(normalizedId, ignoreCase = true) ||
                                config.guestTokenResourceId.equals(normalizedId, ignoreCase = true)
                        }
                        .mapNotNull { it.guestTokenResourceId?.trim() }
                        .firstOrNull { it.isNotBlank() && it.all(Char::isDigit) }
                        ?.trim()
                }
            }
            .distinct()
    }

    @Transactional
    fun create(req: DashboardEmbedConfigRequest): DashboardEmbedConfigResponse =
        dashboardEmbedConfigRepository.save(
            DashboardEmbedConfig(
                label = req.label.trim(),
                description = req.description?.trim()?.ifBlank { null },
                dashboardId = req.dashboardId.trim(),
                guestTokenResourceId = req.guestTokenResourceId?.trim()?.ifBlank { null },
                sortOrder = req.sortOrder,
                enabled = req.enabled,
                allowedRoles = normalizeRoles(req.allowedRoles).toMutableList(),
            )
        ).toResponse()

    @Transactional
    fun update(id: UUID, req: DashboardEmbedConfigRequest): DashboardEmbedConfigResponse {
        val config = dashboardEmbedConfigRepository.findById(id)
            .orElseThrow { NotFoundException("Dashboard embed config not found") }

        config.label = req.label.trim()
        config.description = req.description?.trim()?.ifBlank { null }
        config.dashboardId = req.dashboardId.trim()
        config.guestTokenResourceId = req.guestTokenResourceId?.trim()?.ifBlank { null }
        config.sortOrder = req.sortOrder
        config.enabled = req.enabled
        config.allowedRoles = normalizeRoles(req.allowedRoles).toMutableList()

        return dashboardEmbedConfigRepository.save(config).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val config = dashboardEmbedConfigRepository.findById(id)
            .orElseThrow { NotFoundException("Dashboard embed config not found") }
        dashboardEmbedConfigRepository.delete(config)
    }

    private fun normalizeRoles(roles: List<String>): List<String> =
        roles.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()

    private fun DashboardEmbedConfig.toResponse() = DashboardEmbedConfigResponse(
        id = id.toString(),
        label = label,
        description = description,
        dashboardId = dashboardId,
        guestTokenResourceId = guestTokenResourceId,
        sortOrder = sortOrder,
        enabled = enabled,
        allowedRoles = allowedRoles.map { it.uppercase() }.sorted(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
