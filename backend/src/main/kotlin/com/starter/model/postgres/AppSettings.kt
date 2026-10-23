package com.starter.model.postgres

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore
import com.starter.security.EncryptedSecretConverter
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "app_settings")
@EntityListeners(AuditingEntityListener::class)
data class AppSettings(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "storage_provider", nullable = false)
    var storageProvider: String = "NONE",

    @Column(name = "storage_bucket")
    var storageBucket: String? = null,

    @Column(name = "storage_region")
    var storageRegion: String? = null,

    @Column(name = "storage_endpoint")
    var storageEndpoint: String? = null,

    @get:JsonIgnore
    @Convert(converter = EncryptedSecretConverter::class)
    @Column(name = "storage_access_key", columnDefinition = "TEXT")
    var storageAccessKey: String? = null,

    @get:JsonIgnore
    @Convert(converter = EncryptedSecretConverter::class)
    @Column(name = "storage_secret_key", columnDefinition = "TEXT")
    var storageSecretKey: String? = null,

    @Column(name = "storage_root_path", nullable = false)
    @ColumnDefault("'folders'")
    var storageRootPath: String = "folders",

    @Column(name = "vector_reindex_enabled", nullable = false)
    var vectorReindexEnabled: Boolean = false,

    @Column(name = "vector_reindex_interval_minutes", nullable = false)
    var vectorReindexIntervalMinutes: Int = 1440,

    @Column(name = "vector_reindex_schedule", nullable = false) @ColumnDefault("'INTERVAL'")
    var vectorReindexSchedule: String = "INTERVAL",
    @Column(name = "vector_reindex_time", nullable = false) @ColumnDefault("'00:00'")
    var vectorReindexTime: String = "00:00",
    @Column(name = "vector_reindex_day_of_week", nullable = false) @ColumnDefault("'FRIDAY'")
    var vectorReindexDayOfWeek: String = "FRIDAY",
    @Column(name = "vector_reindex_time_zone", nullable = false) @ColumnDefault("'Africa/Johannesburg'")
    var vectorReindexTimeZone: String = "Africa/Johannesburg",
    @Column(name = "vector_reindex_next_run_at")
    var vectorReindexNextRunAt: Instant? = null,
    @Column(name = "vector_reindex_pending_changes", nullable = false) @ColumnDefault("false")
    var vectorReindexPendingChanges: Boolean = false,
    @Column(name = "vector_reindex_last_check_at")
    var vectorReindexLastCheckAt: Instant? = null,

    @Column(name = "last_vector_reindex_at")
    var lastVectorReindexAt: Instant? = null,

    @Column(name = "last_vector_reindex_status")
    var lastVectorReindexStatus: String? = null,

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    override fun toString() = "AppSettings(id=$id, storageProvider=$storageProvider)"
}
