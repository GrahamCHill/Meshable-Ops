package com.starter.repository.postgres

import com.starter.model.postgres.BrandingAsset
import com.starter.model.postgres.BrandingKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BrandingAssetRepository : JpaRepository<BrandingAsset, BrandingKind> {
    @Query("select new com.starter.dto.BrandingVersion(b.kind, b.version) from BrandingAsset b")
    fun versions(): List<com.starter.dto.BrandingVersion>
}
