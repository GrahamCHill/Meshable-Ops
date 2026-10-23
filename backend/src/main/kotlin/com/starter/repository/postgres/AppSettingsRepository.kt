package com.starter.repository.postgres

import com.starter.model.postgres.AppSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppSettingsRepository : JpaRepository<AppSettings, UUID> {
    fun findTopByOrderByCreatedAtAsc(): AppSettings?
}
