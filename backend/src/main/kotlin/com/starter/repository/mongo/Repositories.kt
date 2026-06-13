package com.starter.repository.mongo

import com.starter.model.mongo.ActivityLog
import com.starter.model.mongo.Event
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface EventRepository : MongoRepository<Event, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Event>
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Event>
    fun findByUserIdAndType(userId: String, type: String): List<Event>
    fun findByUserIdAndCreatedAtBetween(userId: String, from: Instant, to: Instant): List<Event>
    fun countByUserId(userId: String): Long
}

interface ActivityLogRepository : MongoRepository<ActivityLog, String> {
    fun findAllByOrderByTimestampDesc(pageable: Pageable): Page<ActivityLog>
    fun findByUserIdOrderByTimestampDesc(userId: String, pageable: Pageable): Page<ActivityLog>
}
