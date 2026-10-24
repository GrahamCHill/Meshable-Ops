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
    private val supersetService: SupersetService,
) {

    @Transactional
    fun listEnabledForRole(role: String): List<DashboardEmbedConfigResponse> =
        dashboardEmbedConfigRepository.findAllByEnabledTrueOrderBySortOrderAscCreatedAtAsc()
            .filter { config ->
                role == "SUPER_ADMIN" || config.allowedRoles.isEmpty() || config.allowedRoles.any { it.equals(role, ignoreCase = true) }
            }
            .map { refreshLink(it).toResponse() }

    @Transactional
    fun listAll(): List<DashboardEmbedConfigResponse> =
        dashboardEmbedConfigRepository.findAllByOrderBySortOrderAscCreatedAtAsc().map { refreshLink(it).toResponse() }

    @Transactional(readOnly = true)
    fun listAvailableRoles(): List<String> = (userRepository.findDistinctRoles() + com.starter.access.Roles.all).distinct().sorted()

    @Transactional(readOnly = true)
    fun resolveGuestTokenDashboardIds(requestedIds: List<String>, role: String? = null): List<String> {
        if (requestedIds.isEmpty()) return emptyList()

        val configs = dashboardEmbedConfigRepository.findAll().filter { role == null || it.enabled && (role == "SUPER_ADMIN" || it.allowedRoles.isEmpty() || role in it.allowedRoles) }
        return requestedIds
            .mapNotNull { requestedId ->
                val normalizedId = requestedId.trim()
                when {
                    normalizedId.isBlank() -> null
                    normalizedId.all(Char::isDigit) -> normalizedId.takeIf { role == null || configs.any { it.guestTokenResourceId == normalizedId } }
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
    fun create(req: DashboardEmbedConfigRequest): DashboardEmbedConfigResponse {
        val link = supersetService.resolveDashboardLink(req.supersetDashboardId)
        return dashboardEmbedConfigRepository.save(
            DashboardEmbedConfig(
                label = req.label.trim(),
                description = req.description?.trim()?.ifBlank { null },
                dashboardId = link.embedUuid,
                guestTokenResourceId = link.numericId,
                sortOrder = req.sortOrder,
                enabled = req.enabled,
                allowedRoles = normalizeRoles(req.allowedRoles).toMutableList(),
            )
        ).toResponse()
    }

    @Transactional
    fun update(id: UUID, req: DashboardEmbedConfigRequest): DashboardEmbedConfigResponse {
        val config = dashboardEmbedConfigRepository.findById(id)
            .orElseThrow { NotFoundException("Dashboard embed config not found") }

        config.label = req.label.trim()
        config.description = req.description?.trim()?.ifBlank { null }
        val link = supersetService.resolveDashboardLink(req.supersetDashboardId)
        config.dashboardId = link.embedUuid
        config.guestTokenResourceId = link.numericId
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

    private fun refreshLink(config: DashboardEmbedConfig): DashboardEmbedConfig {
        val numericId = config.guestTokenResourceId?.toIntOrNull() ?: return config
        val link = supersetService.resolveDashboardLink(numericId)
        if (config.dashboardId != link.embedUuid || config.guestTokenResourceId != link.numericId) {
            config.dashboardId = link.embedUuid
            config.guestTokenResourceId = link.numericId
            return dashboardEmbedConfigRepository.save(config)
        }
        return config
    }

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
