package com.starter.repository.postgres

import com.starter.model.postgres.Item
import com.starter.model.postgres.StorageDocument
import com.starter.model.postgres.AppSettings
import com.starter.model.postgres.DashboardEmbedConfig
import com.starter.model.postgres.User
import com.starter.model.postgres.StorageConnection
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): Optional<User>
    fun findByUsername(username: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun existsByUsername(username: String): Boolean
    fun findAllByOrderByRoleAscCreatedAtAsc(): List<User>

    @Query("SELECT DISTINCT UPPER(u.role) FROM User u ORDER BY UPPER(u.role)")
    fun findDistinctRoles(): List<String>
}

interface ItemRepository : JpaRepository<Item, UUID> {
    @Query("SELECT i FROM Item i LEFT JOIN i.storageConnection c WHERE i.accessible = true AND (c IS NULL OR c.enabled = true) AND ((:connectionId IS NULL AND c IS NULL) OR c.id = :connectionId) ORDER BY i.createdAt DESC")
    fun findVisibleByConnection(connectionId: UUID?, pageable: Pageable): Page<Item>

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

interface StorageConnectionRepository : JpaRepository<StorageConnection, UUID> {
    fun findAllByOrderByNicknameAsc(): List<StorageConnection>
    fun findAllByEnabledTrueOrderByNicknameAsc(): List<StorageConnection>
}

interface StorageDocumentRepository : JpaRepository<StorageDocument, UUID> {
    fun findByItemIdAndStoragePath(itemId: UUID, storagePath: String): StorageDocument?
    fun findAllByItemId(itemId: UUID): List<StorageDocument>
}

interface DashboardEmbedConfigRepository : JpaRepository<DashboardEmbedConfig, UUID> {
    fun findAllByEnabledTrueOrderBySortOrderAscCreatedAtAsc(): List<DashboardEmbedConfig>
    fun findAllByOrderBySortOrderAscCreatedAtAsc(): List<DashboardEmbedConfig>
}

interface AppSettingsRepository : JpaRepository<AppSettings, UUID> {
    fun findTopByOrderByCreatedAtAsc(): AppSettings?
}
