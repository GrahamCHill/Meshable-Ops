package com.starter.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private val schedulerLog = LoggerFactory.getLogger(VectorReindexScheduler::class.java)

@Component
class VectorReindexScheduler(
    private val itemService: ItemService,
    private val adminSettingsService: AdminSettingsService,
    private val auditLogService: AuditLogService,
) {
    @Scheduled(fixedDelay = 60000)
    fun maybeReindexVectors() {
        val settings = adminSettingsService.currentEntity()
        if (!settings.vectorReindexEnabled) return

        val lastRun = settings.lastVectorReindexAt
        if (lastRun != null) {
            val elapsedMinutes = Duration.between(lastRun, Instant.now()).toMinutes()
            if (elapsedMinutes < settings.vectorReindexIntervalMinutes) return
        }

        try {
            val count = itemService.reindexShared()
            adminSettingsService.recordReindexRun(count, "scheduler")
            auditLogService.record(
                actingEmail = null,
                action = "VECTOR_REINDEX_AUTO",
                resourceType = "ITEM",
                resourceId = count.toString(),
            )
            schedulerLog.info("Auto reindexed {} folders", count)
        } catch (e: Exception) {
            adminSettingsService.recordReindexFailure(e.message ?: "Unknown error", "scheduler")
            schedulerLog.error("Scheduled vector reindex failed: {}", e.message)
        }
    }
}
