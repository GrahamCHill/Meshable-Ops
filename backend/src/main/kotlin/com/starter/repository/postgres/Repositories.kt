package com.starter.repository.postgres

import com.starter.model.postgres.Item
import com.starter.model.postgres.DashboardEmbedConfig
import com.starter.model.postgres.User
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
}

interface ItemRepository : JpaRepository<Item, UUID> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Item>
    fun findAllByOrderByCreatedAtDesc(): List<Item>

    @Query("SELECT i FROM Item i WHERE i.user.id = :userId AND :tag MEMBER OF i.tags")
    fun findByUserIdAndTag(userId: UUID, tag: String): List<Item>
}

interface DashboardEmbedConfigRepository : JpaRepository<DashboardEmbedConfig, UUID> {
    fun findAllByEnabledTrueOrderBySortOrderAscCreatedAtAsc(): List<DashboardEmbedConfig>
    fun findAllByOrderBySortOrderAscCreatedAtAsc(): List<DashboardEmbedConfig>
}
