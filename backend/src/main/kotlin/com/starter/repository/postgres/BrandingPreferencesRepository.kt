package com.starter.repository.postgres

import com.starter.model.postgres.BrandingPreferencesRecord
import org.springframework.data.jpa.repository.JpaRepository

interface BrandingPreferencesRepository : JpaRepository<BrandingPreferencesRecord, Int>
