package com.starter.model.postgres

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.util.UUID

@Entity @Table(name = "user_profiles")
class UserProfile(@Id val userId: UUID = UUID.randomUUID(), var timeZone: String = "UTC",
    @get:JsonIgnore @Column(columnDefinition = "bytea") var avatar: ByteArray? = null, var avatarVersion: String? = null,
    @Version var revision: Long = 0)
