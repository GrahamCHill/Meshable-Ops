package com.starter.model.postgres

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.security.EncryptedSecretConverter
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "storage_connections")
@EntityListeners(AuditingEntityListener::class)
data class StorageConnection(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var nickname: String = "",
    @Column(nullable = false) var provider: String = "S3",
    @Column(nullable = false) var bucket: String = "",
    var region: String? = null,
    @Column(columnDefinition = "TEXT") var endpoint: String? = null,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(name = "access_key", columnDefinition = "TEXT") var accessKey: String? = null,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class)
    @Column(name = "secret_key", columnDefinition = "TEXT") var secretKey: String? = null,
    @Column(name = "base_path", nullable = false, columnDefinition = "TEXT") var basePath: String = "",
    @Column(nullable = false) var enabled: Boolean = true,
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @LastModifiedDate @Column(name = "updated_at", nullable = false) val updatedAt: Instant = Instant.now(),
) {
    override fun toString() = "StorageConnection(id=$id, nickname=$nickname, provider=$provider)"
    fun settings() = AppSettings(
        storageProvider = provider, storageBucket = bucket, storageRegion = region,
        storageEndpoint = endpoint, storageAccessKey = accessKey, storageSecretKey = secretKey,
        storageRootPath = basePath.ifBlank { "folders" },
    )
}
