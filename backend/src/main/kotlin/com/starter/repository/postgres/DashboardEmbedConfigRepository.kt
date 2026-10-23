package com.starter.repository.postgres

import com.starter.model.postgres.DashboardEmbedConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DashboardEmbedConfigRepository : JpaRepository<DashboardEmbedConfig, UUID> {
    fun findAllByEnabledTrueOrderBySortOrderAscCreatedAtAsc(): List<DashboardEmbedConfig>
    fun findAllByOrderBySortOrderAscCreatedAtAsc(): List<DashboardEmbedConfig>
}
