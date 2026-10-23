package com.starter.repository.postgres

import com.starter.model.postgres.AiSettings
import org.springframework.data.jpa.repository.JpaRepository

interface AiSettingsRepository : JpaRepository<AiSettings, Long>
