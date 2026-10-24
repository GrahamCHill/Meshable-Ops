package com.starter.repository.postgres

import com.starter.dto.LoginImageView
import com.starter.model.postgres.BrandingLoginImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BrandingLoginImageRepository : JpaRepository<BrandingLoginImage, Int> {
    @Query("select new com.starter.dto.LoginImageView(b.slot, b.version) from BrandingLoginImage b order by b.slot")
    fun versions(): List<LoginImageView>
}
