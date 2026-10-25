package com.starter.service

import com.starter.exception.BadRequestException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.starter.service.pdfstorage.GeneratedPdfDestinations
import com.starter.service.pdfstorage.PdfStorageDestination

@Service
class GeneratedPdfCleanupService(private val store: GeneratedPdfCleanupStore, private val audit: AuditLogService,
    private val destinations: GeneratedPdfDestinations? = null) {
    @Transactional(readOnly = true)
    fun view(): PdfCleanupView {
        val settings = store.settings()
        return PdfCleanupView(settings, store.eligible(Instant.now().minus(settings.retentionDays.toLong(), ChronoUnit.DAYS)), destinations?.status())
    }

    @Transactional
    fun update(input: PdfCleanupInput, email: String): PdfCleanupView {
        if (input.retentionDays !in 1..3650) throw BadRequestException("Keep generated PDFs for between 1 and 3650 days")
        if (input.destination == PdfStorageDestination.CONNECTION && input.connectionId == null) throw BadRequestException("Choose a storage connection")
        if (input.destination != PdfStorageDestination.CONNECTION && input.connectionId != null) throw BadRequestException("Choose a connection only when using a named storage connection")
        val previous = store.settings(lock = true)
        if (previous.destination != input.destination || previous.connectionId != input.connectionId) destinations?.resolve(input.destination, input.connectionId)
        val next = if (previous.schedule == input.schedule) previous.nextRunAt else input.schedule.next(Instant.now())
        store.save(input, next)
        audit.record(email, "GENERATED_PDF_CLEANUP_SETTINGS_UPDATED", "GENERATED_PDF_CLEANUP", "${input.schedule}:${input.retentionDays}:${input.destination}:${input.connectionId ?: "default"}")
        return view()
    }

    @Transactional
    fun clearExpired(email: String? = null, scheduled: Boolean = false, now: Instant = Instant.now()): PdfCleanupCounts? {
        // The singleton row serializes cleanup workers and settings updates across all server instances.
        val settings = store.settings(lock = true)
        if (scheduled && (settings.schedule == PdfCleanupSchedule.NEVER || settings.nextRunAt?.isAfter(now) != false)) return null
        val result = store.clear(now.minus(settings.retentionDays.toLong(), ChronoUnit.DAYS))
        store.record(now, settings.schedule.next(now), result)
        audit.record(email, if (scheduled) "GENERATED_PDF_CLEANUP_AUTO" else "GENERATED_PDF_CLEANUP_MANUAL", "GENERATED_PDF_CLEANUP",
            "${result.reports} reports:${result.documents} template PDFs:${result.bytes} bytes")
        return result
    }

    @Transactional
    fun recordFailure() = store.failure(Instant.now())
}
