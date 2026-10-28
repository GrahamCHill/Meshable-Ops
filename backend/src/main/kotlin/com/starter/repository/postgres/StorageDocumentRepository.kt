package com.starter.repository.postgres

import com.starter.model.postgres.StorageDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import org.springframework.data.jpa.repository.Query

interface StorageDocumentRepository : JpaRepository<StorageDocument, UUID> {
    @Query("select d from StorageDocument d join fetch d.item i left join fetch i.storageConnection where d.id=:id")
    fun findForProcessing(id: UUID): StorageDocument?
    fun findByItemIdAndStoragePath(itemId: UUID, storagePath: String): StorageDocument?
    fun findAllByItemId(itemId: UUID): List<StorageDocument>
    @Query("SELECT new com.starter.repository.postgres.StorageDocumentListing(d.id, d.storagePath, d.sourceChecksum, d.processingStatus, d.vectorizedAt) FROM StorageDocument d WHERE d.item.id=:itemId AND d.storagePath IN :paths")
    fun findListingByItemIdAndStoragePathIn(itemId: UUID, paths: Collection<String>): List<StorageDocumentListing>

    @Query("SELECT d.item.id AS itemId, d.storagePath AS storagePath FROM StorageDocument d WHERE d.item.id IN :itemIds")
    fun findPathsByItemIds(itemIds: Collection<UUID>): List<StorageDocumentPath>
}

interface StorageDocumentPath {
    val itemId: UUID
    val storagePath: String
}

data class StorageDocumentListing(val id: UUID, val storagePath: String, val sourceChecksum: String?,
    val processingStatus: String, val vectorizedAt: java.time.Instant?)
