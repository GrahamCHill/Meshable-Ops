package com.starter.model.postgres

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.Instant

enum class BrandingKind { LOGO, FAVICON }

@Entity
@Table(name = "branding_assets")
class BrandingAsset(
    @Id @Enumerated(EnumType.STRING) val kind: BrandingKind = BrandingKind.LOGO,
    @get:JsonIgnore @Column(columnDefinition = "bytea", nullable = false) var png: ByteArray = byteArrayOf(),
    @Column(nullable = false, length = 64) var version: String = "",
    @Column(nullable = false) var updatedAt: Instant = Instant.now(),
) {
    override fun toString() = "BrandingAsset(kind=$kind, version=$version)"
}
