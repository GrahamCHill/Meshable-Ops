package com.starter.service

import com.starter.dto.DashboardEmbedConfigRequest
import com.starter.dto.DashboardEmbedConfigResponse
import com.starter.exception.NotFoundException
import com.starter.model.postgres.DashboardEmbedConfig
import com.starter.repository.postgres.DashboardEmbedConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DashboardEmbedConfigService(
    private val dashboardEmbedConfigRepository: DashboardEmbedConfigRepository
) {

    @Transactional(readOnly = true)
    fun listEnabled(): List<DashboardEmbedConfigResponse> =
        dashboardEmbedConfigRepository.findAllByEnabledTrueOrderBySortOrderAscCreatedAtAsc().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun listAll(): List<DashboardEmbedConfigResponse> =
        dashboardEmbedConfigRepository.findAllByOrderBySortOrderAscCreatedAtAsc().map { it.toResponse() }

    @Transactional
    fun create(req: DashboardEmbedConfigRequest): DashboardEmbedConfigResponse =
        dashboardEmbedConfigRepository.save(
            DashboardEmbedConfig(
                label = req.label.trim(),
                description = req.description?.trim()?.ifBlank { null },
                dashboardId = req.dashboardId.trim(),
                guestTokenResourceId = req.guestTokenResourceId?.trim()?.ifBlank { null },
                sortOrder = req.sortOrder,
                enabled = req.enabled
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

        return dashboardEmbedConfigRepository.save(config).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val config = dashboardEmbedConfigRepository.findById(id)
            .orElseThrow { NotFoundException("Dashboard embed config not found") }
        dashboardEmbedConfigRepository.delete(config)
    }

    private fun DashboardEmbedConfig.toResponse() = DashboardEmbedConfigResponse(
        id = id.toString(),
        label = label,
        description = description,
        dashboardId = dashboardId,
        guestTokenResourceId = guestTokenResourceId,
        sortOrder = sortOrder,
        enabled = enabled,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
