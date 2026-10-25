package com.starter.service

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.time.temporal.ChronoUnit

class GeneratedPdfCleanupServiceTest {
    private val store = mock(GeneratedPdfCleanupStore::class.java)
    private val audit = mock(AuditLogService::class.java)
    private val service = GeneratedPdfCleanupService(store, audit)
    private val now = Instant.parse("2026-10-25T10:00:00Z")

    @Test fun `never and future schedules cannot clear anything automatically`() {
        for (settings in listOf(PdfCleanupSettings(), PdfCleanupSettings(PdfCleanupSchedule.DAILY, nextRunAt = now.plusSeconds(1)))) {
            `when`(store.settings(true)).thenReturn(settings)
            assertThat(service.clearExpired(scheduled = true, now = now)).isNull()
        }
        verify(store, never()).clear(any(Instant::class.java) ?: now)
        verifyNoInteractions(audit)
    }

    @Test fun `a due job uses the retention cutoff records counts and advances the persisted schedule`() {
        val counts = PdfCleanupCounts(2, 3, 5000)
        `when`(store.settings(true)).thenReturn(PdfCleanupSettings(PdfCleanupSchedule.WEEKLY, 7, now.minusSeconds(100)))
        `when`(store.clear(now.minus(7, ChronoUnit.DAYS))).thenReturn(counts)
        assertThat(service.clearExpired(scheduled = true, now = now)).isEqualTo(counts)
        verify(store).record(now, now.plus(7, ChronoUnit.DAYS), counts)
        verify(audit).record(null, "GENERATED_PDF_CLEANUP_AUTO", "GENERATED_PDF_CLEANUP", "2 reports:3 template PDFs:5000 bytes")
    }

    @Test fun `manual cleanup works with automatic cleanup disabled and keeps it disabled`() {
        `when`(store.settings(true)).thenReturn(PdfCleanupSettings(retentionDays = 30))
        `when`(store.clear(now.minus(30, ChronoUnit.DAYS))).thenReturn(PdfCleanupCounts())
        assertThat(service.clearExpired("admin@example.test", now = now)).isEqualTo(PdfCleanupCounts())
        verify(store).record(now, null, PdfCleanupCounts())
    }

    @Test fun `invalid retention is rejected before settings or files can change`() {
        for (days in listOf(-1, 0, 3651)) {
            assertThatThrownBy { service.update(PdfCleanupInput(retentionDays = days), "admin@example.test") }.isInstanceOf(BadRequestException::class.java)
        }
        verifyNoInteractions(store, audit)
    }

    @Test fun `choosing never cancels the persisted run and preserving a schedule keeps its next run`() {
        val next = now.plus(1, ChronoUnit.DAYS)
        `when`(store.settings(true)).thenReturn(PdfCleanupSettings(PdfCleanupSchedule.DAILY, nextRunAt = next))
        `when`(store.settings(false)).thenReturn(PdfCleanupSettings())
        `when`(store.eligible(any(Instant::class.java) ?: now)).thenReturn(PdfCleanupCounts())
        service.update(PdfCleanupInput(PdfCleanupSchedule.NEVER, 7), "admin@example.test")
        verify(store).save(PdfCleanupInput(PdfCleanupSchedule.NEVER, 7), null)
        service.update(PdfCleanupInput(PdfCleanupSchedule.DAILY, 90), "admin@example.test")
        verify(store).save(PdfCleanupInput(PdfCleanupSchedule.DAILY, 90), next)
    }

    @Test fun `scheduler waits for startup and records retry information after a failed transaction`() {
        val cleanup = mock(GeneratedPdfCleanupService::class.java)
        val scheduler = GeneratedPdfCleanupScheduler(cleanup)
        scheduler.cleanup()
        verifyNoInteractions(cleanup)
        scheduler.applicationReady()
        `when`(cleanup.clearExpired(isNull(), eq(true), any(Instant::class.java) ?: now)).thenThrow(IllegalStateException("test failure"))
        scheduler.cleanup()
        verify(cleanup).recordFailure()
    }
}
