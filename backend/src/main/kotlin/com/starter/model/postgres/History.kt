package com.starter.model.postgres

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "events", indexes = [Index(name = "idx_events_history", columnList = "created_at,id"), Index(name = "idx_events_user", columnList = "user_id,created_at")])
data class Event(
    @Id @Column(length = 512) val id: String = UUID.randomUUID().toString(),
    @Column(name = "user_id", nullable = false) val userId: String = "",
    @Column(nullable = false) val type: String = "",
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) val payload: Map<String, Any> = emptyMap(),
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "activity_logs", indexes = [Index(name = "idx_activity_history", columnList = "timestamp,id"), Index(name = "idx_activity_user", columnList = "user_id,timestamp")])
data class ActivityLog(
    @Id @Column(length = 512) val id: String = UUID.randomUUID().toString(),
    @Column(name = "user_id", nullable = false) val userId: String = "",
    @Column(nullable = false) val action: String = "",
    val resourceType: String? = null,
    @Column(columnDefinition = "text") val resourceId: String? = null,
    val ipAddress: String? = null,
    @Column(columnDefinition = "text") val userAgent: String? = null,
    @Column(nullable = false) val timestamp: Instant = Instant.now(),
)
