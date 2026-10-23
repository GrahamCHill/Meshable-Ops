package com.starter.repository.postgres

import com.starter.model.postgres.DocumentAccessSettings
import org.springframework.data.jpa.repository.JpaRepository

interface DocumentAccessSettingsRepository : JpaRepository<DocumentAccessSettings, Long>
