package com.starter.repository.postgres

import com.starter.model.postgres.User
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
