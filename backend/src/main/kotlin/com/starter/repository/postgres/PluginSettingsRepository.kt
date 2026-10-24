package com.starter.repository.postgres

import com.starter.model.postgres.PluginSettings
import org.springframework.data.jpa.repository.JpaRepository

interface PluginSettingsRepository : JpaRepository<PluginSettings, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PluginSettings p WHERE p.pluginId IN :ids ORDER BY p.pluginId")
    fun lockForActivation(ids: List<String>): List<PluginSettings>
}
