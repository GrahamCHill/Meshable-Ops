package com.starter.model.postgres

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.Instant

@Entity @Table(name = "branding_login_images")
class BrandingLoginImage(
    // A nullable wrapper prevents Spring Data from treating the valid slot zero as an unsaved primitive ID.
    @Id @Column(name = "image_slot") val slot: Int? = null,
    @get:JsonIgnore @Column(columnDefinition = "bytea", nullable = false) var jpeg: ByteArray = byteArrayOf(),
    @Column(nullable = false, length = 64) var version: String = "",
    @Column(nullable = false) var updatedAt: Instant = Instant.now(),
)
