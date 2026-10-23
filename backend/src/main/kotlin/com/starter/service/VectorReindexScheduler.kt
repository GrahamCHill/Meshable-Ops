package com.starter.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import java.time.Instant

private val schedulerLog = LoggerFactory.getLogger(VectorReindexScheduler::class.java)

@Component
class VectorReindexScheduler(
    private val itemService: ItemService,
    private val adminSettingsService: AdminSettingsService,
    private val auditLogService: AuditLogService,
) {
    @Volatile private var ready = false

    @EventListener(ApplicationReadyEvent::class)
    fun applicationReady() { ready = true }

    @Scheduled(fixedDelay = 60000)
    fun maybeReindexVectors() {
        // Scheduled tasks can start before ApplicationRunner credential migration completes.
        if (!ready) return
        var settings = adminSettingsService.currentEntity()
        if (!settings.vectorReindexEnabled) return
        val now = Instant.now()
        try {
            if (settings.vectorReindexSchedule == "ON_CHANGE") {
                val changed = itemService.hasStorageChanges()
                settings = adminSettingsService.recordChangeCheck(changed, now)
                if (!settings.vectorReindexPendingChanges) return
            } else if (settings.vectorReindexNextRunAt == null) settings = adminSettingsService.initializeNextRun(now)
            if (settings.vectorReindexNextRunAt?.isAfter(now) != false) return
            val result = itemService.reindexShared()
            adminSettingsService.recordReindexRun(result.folders, result.documents, "scheduler", result.failures)
            auditLogService.record(
                actingEmail = null,
                action = "VECTOR_REINDEX_AUTO",
                resourceType = "VECTOR_INDEX",
                resourceId = "${result.folders}:${result.documents}",
            )
            schedulerLog.info("Auto reindexed {} folders and {} documents", result.folders, result.documents)
        } catch (e: Exception) {
            adminSettingsService.recordReindexFailure(e.message ?: "Unknown error", "scheduler")
            schedulerLog.error("Scheduled vector reindex failed: {}", e.message)
        }
    }
}
