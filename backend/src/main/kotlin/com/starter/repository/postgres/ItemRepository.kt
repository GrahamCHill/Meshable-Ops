package com.starter.repository.postgres

import com.starter.model.postgres.Item
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ItemRepository : JpaRepository<Item, UUID> {
    @Query("SELECT i.id AS id, c.id AS connectionId FROM Item i JOIN i.storageConnection c WHERE i.accessible=true AND c.enabled=true")
    fun findVisibleConnectionRoots(): List<StorageConnectionRoot>
    @Query("SELECT count(i) FROM Item i LEFT JOIN i.storageConnection c WHERE i.id = :id AND i.accessible = true AND (c IS NULL OR c.enabled = true)")
    fun countVisibleById(id: UUID): Long
    @Query("SELECT i FROM Item i LEFT JOIN i.storageConnection c WHERE i.accessible = true AND (c IS NULL OR c.enabled = true) AND ((:connectionId IS NULL AND c IS NULL) OR c.id = :connectionId) ORDER BY i.createdAt DESC")
    fun findVisibleByConnection(connectionId: UUID?, pageable: Pageable): Page<Item>

    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.storageConnection c WHERE i.accessible = true AND (c IS NULL OR c.enabled = true) AND ((:connectionId IS NULL AND c IS NULL) OR c.id = :connectionId) ORDER BY i.createdAt DESC")
    fun findVisibleRootsByConnection(connectionId: UUID?): List<Item>

    @Query("SELECT i FROM Item i LEFT JOIN i.storageConnection c WHERE i.accessible = true AND (c IS NULL OR c.enabled = true) ORDER BY i.createdAt DESC")
    fun findAllVisible(): List<Item>
    fun findAllByStorageConnectionIdOrderByTitleAsc(connectionId: UUID): List<Item>
    fun findAllByStorageConnectionIsNullOrderByTitleAsc(): List<Item>
    fun countByStorageConnectionIdAndAccessibleTrue(connectionId: UUID): Long
    fun existsByStorageConnectionIdAndStoragePrefix(connectionId: UUID, prefix: String): Boolean
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Item>
    fun findAllByOrderByCreatedAtDesc(): List<Item>

    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND :tag MEMBER OF i.tags")
    fun findByUserIdAndTag(userId: UUID, tag: String): List<Item>
}

interface StorageConnectionRoot {
    val id: UUID
    val connectionId: UUID
}
