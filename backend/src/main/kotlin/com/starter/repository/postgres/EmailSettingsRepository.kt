package com.starter.repository.postgres

import com.starter.model.postgres.EmailSettings
import org.springframework.data.jpa.repository.JpaRepository

interface EmailSettingsRepository : JpaRepository<EmailSettings, Long>
