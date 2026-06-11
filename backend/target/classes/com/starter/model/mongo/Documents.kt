package com.starter.model.mongo

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "events")
data class Event(
    @Id val id: String? = null,
    @Indexed(name = "userId_1") val userId: String = "",
    @Indexed(name = "type_1") val type: String = "",
    val payload: Map<String, Any> = emptyMap(),
    @Indexed(name = "createdAt_1") val createdAt: Instant = Instant.now()
)

@Document(collection = "activity_logs")
data class ActivityLog(
    @Id val id: String? = null,
    @Indexed(name = "userId_1") val userId: String = "",
    val action: String = "",
    val resourceType: String? = null,
    val resourceId: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val timestamp: Instant = Instant.now()
)
