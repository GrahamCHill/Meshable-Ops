package com.starter.model.postgres

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "storage_documents",
    uniqueConstraints = [UniqueConstraint(name = "uk_storage_documents_item_path", columnNames = ["item_id", "storage_path"])],
)
@EntityListeners(AuditingEntityListener::class)
data class StorageDocument(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    val item: Item = Item(),

    @Column(name = "storage_path", nullable = false, columnDefinition = "TEXT")
    var storagePath: String = "",

    @Column(name = "file_name", nullable = false)
    var fileName: String = "",

    @Column(name = "content_type")
    var contentType: String? = null,

    @Column(name = "size_bytes")
    var sizeBytes: Long? = null,

    @Column(name = "storage_last_modified")
    var storageLastModified: String? = null,

    @Column(name = "processing_status", nullable = false)
    var processingStatus: String = "METADATA_ONLY",

    @Column(name = "source_checksum")
    var sourceChecksum: String? = null,

    @Column(name = "embedding_model")
    var embeddingModel: String? = null,

    @Column(name = "vectorized_at")
    var vectorizedAt: Instant? = null,

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
