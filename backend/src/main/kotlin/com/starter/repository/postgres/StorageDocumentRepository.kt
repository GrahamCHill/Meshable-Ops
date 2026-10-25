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
}
