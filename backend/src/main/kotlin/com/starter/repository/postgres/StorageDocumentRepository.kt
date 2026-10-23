package com.starter.repository.postgres

import com.starter.model.postgres.StorageDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorageDocumentRepository : JpaRepository<StorageDocument, UUID> {
    fun findByItemIdAndStoragePath(itemId: UUID, storagePath: String): StorageDocument?
    fun findAllByItemId(itemId: UUID): List<StorageDocument>
}
