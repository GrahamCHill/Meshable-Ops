package com.starter.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GeneratedPdfCleanupScheduler(private val service: GeneratedPdfCleanupService) {
    @Volatile private var ready = false

    @EventListener(ApplicationReadyEvent::class)
    fun applicationReady() { ready = true }

    @Scheduled(fixedDelay = 60_000)
    fun cleanup() {
        if (!ready) return
        try {
            service.clearExpired(scheduled = true)
        } catch (error: Exception) {
            LoggerFactory.getLogger(javaClass).error("Generated PDF cleanup failed", error)
            service.recordFailure()
        }
    }
}
