package com.starter.security

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

class TooManyRequestsException : RuntimeException("Too many attempts. Wait a few minutes and try again.")

@Entity @Table(name = "security_rate_limits")
class RateLimitRecord(@Id val id: String = "", var attempts: Int = 0, var expiresAt: Instant = Instant.EPOCH)

@Service
class RateLimits(private val jdbc: JdbcTemplate) {
    @org.springframework.transaction.annotation.Transactional(noRollbackFor = [TooManyRequestsException::class])
    fun check(scope: String, identity: String, limit: Int, windowSeconds: Int) {
        val bucket = Instant.now().epochSecond / windowSeconds
        val id = SecureValues.hash("$scope:$identity:$bucket")
        val count = jdbc.queryForObject("""INSERT INTO security_rate_limits(id, attempts, expires_at)
            VALUES (?, 1, ?) ON CONFLICT(id) DO UPDATE SET attempts = security_rate_limits.attempts + 1 RETURNING attempts""",
            Int::class.java, id, java.sql.Timestamp.from(Instant.ofEpochSecond((bucket + 1) * windowSeconds))) ?: limit + 1
        if (count > limit) throw TooManyRequestsException()
    }
    @Scheduled(fixedDelay = 3_600_000) fun purge() { jdbc.update("DELETE FROM security_rate_limits WHERE expires_at < NOW()") }
}
