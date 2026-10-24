package com.starter.repository.postgres
import com.starter.model.postgres.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface UserProfileRepository : JpaRepository<UserProfile, UUID>
