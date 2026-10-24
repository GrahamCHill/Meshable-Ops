package com.starter.repository.postgres

import com.starter.model.postgres.NavigationSettings
import org.springframework.data.jpa.repository.JpaRepository

interface NavigationSettingsRepository : JpaRepository<NavigationSettings, Int>
