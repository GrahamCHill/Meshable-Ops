package com.starter.notification

import com.starter.security.SecureValues
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Store metadata only. Rendered content, recipient addresses, OTPs and credentials never enter this journal. */
@Service
class NotificationDeliveryRecorder(private val jdbc: JdbcTemplate) {
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun claim(event: String,method: String,target: NotificationTarget,recipient: String,key: String): UUID? {
        val id=UUID.randomUUID()
        val hint=when {recipient.contains('@') -> recipient.take(1)+"***@"+recipient.substringAfter('@');recipient.isNotBlank() -> "***"+recipient.takeLast(4);else -> "Configured destination"}
        val added=jdbc.update("INSERT INTO notification_deliveries(id,notification_id,method_id,target_user_id,recipient_hint,status,created_at,deduplication_hash) VALUES (?,?,?,?,?,'SENDING',?,?) ON CONFLICT (deduplication_hash) DO NOTHING",
            id,event,method,target.userId,hint,java.sql.Timestamp.from(Instant.now()),SecureValues.hash("$event:$method:${target.userId ?: recipient}:$key"))
        return id.takeIf {added==1}
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun finish(id: UUID,status: String) {jdbc.update("UPDATE notification_deliveries SET status=?,finished_at=? WHERE id=?",status,java.sql.Timestamp.from(Instant.now()),id)}
    @Scheduled(fixedDelay=86_400_000) fun purge() {jdbc.update("DELETE FROM notification_deliveries WHERE created_at < ?",java.sql.Timestamp.from(Instant.now().minusSeconds(90*86400L)))}
}
