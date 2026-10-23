package com.starter.repository.postgres

import com.starter.model.postgres.StorageConnection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorageConnectionRepository : JpaRepository<StorageConnection, UUID> {
    fun findAllByOrderByNicknameAsc(): List<StorageConnection>
    fun findAllByEnabledTrueOrderByNicknameAsc(): List<StorageConnection>
}
