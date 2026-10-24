package com.starter.repository.postgres

import com.starter.model.postgres.PluginSettings
import org.springframework.data.jpa.repository.JpaRepository

interface PluginSettingsRepository : JpaRepository<PluginSettings, String>
