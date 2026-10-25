package com.starter.repository.postgres

import com.starter.model.postgres.ActivityLog
import com.starter.model.postgres.Event
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EventRepository : JpaRepository<Event, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Event>
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Event>
    fun findByUserIdAndType(userId: String, type: String): List<Event>
    fun findByUserIdAndCreatedAtBetween(userId: String, from: Instant, to: Instant): List<Event>
    fun countByUserId(userId: String): Long
    @Query("select e from Event e order by e.createdAt desc, e.id desc")
    fun history(pageable: Pageable): List<Event>
    @Query("select e from Event e where e.createdAt < :time or (e.createdAt = :time and e.id < :id) order by e.createdAt desc, e.id desc")
    fun historyBefore(time: Instant, id: String, pageable: Pageable): List<Event>
}

interface ActivityLogRepository : JpaRepository<ActivityLog, String> {
    fun findAllByOrderByTimestampDesc(pageable: Pageable): Page<ActivityLog>
    fun findByUserIdOrderByTimestampDesc(userId: String, pageable: Pageable): Page<ActivityLog>
}
