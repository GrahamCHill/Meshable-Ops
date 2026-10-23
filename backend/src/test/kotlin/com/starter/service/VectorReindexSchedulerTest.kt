package com.starter.service

import com.starter.model.postgres.AppSettings
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat

class VectorReindexSchedulerTest {
    @Test fun `change detection queues work without running before the selected time`() {
        val items = mock(ItemService::class.java)
        val settings = mock(AdminSettingsService::class.java)
        val audit = mock(AuditLogService::class.java)
        val state = AppSettings(vectorReindexEnabled = true, vectorReindexSchedule = "ON_CHANGE")
        `when`(settings.currentEntity()).thenReturn(state)
        `when`(items.hasStorageChanges()).thenReturn(true)
        `when`(settings.recordChangeCheck(eq(true), any(Instant::class.java) ?: Instant.EPOCH)).thenAnswer {
            state.vectorReindexPendingChanges = true
            state.vectorReindexNextRunAt = Instant.now().plusSeconds(3600)
            state
        }
        val scheduler = VectorReindexScheduler(items, settings, audit)
        scheduler.applicationReady()
        scheduler.maybeReindexVectors()
        assertThat(state.vectorReindexPendingChanges).isTrue()
        verify(items, never()).reindexShared()
    }
    @Test fun `a persisted due job executes once and records its result`() {
        val items = mock(ItemService::class.java)
        val settings = mock(AdminSettingsService::class.java)
        val audit = mock(AuditLogService::class.java)
        val state = AppSettings(vectorReindexEnabled = true, vectorReindexSchedule = "WEEKLY", vectorReindexNextRunAt = Instant.EPOCH)
        `when`(settings.currentEntity()).thenReturn(state)
        `when`(items.reindexShared()).thenReturn(VectorReindexResult(1, 2))
        doAnswer { state.vectorReindexNextRunAt = Instant.now().plusSeconds(86400); null }.`when`(settings).recordReindexRun(1, 2, "scheduler", 0)
        val scheduler = VectorReindexScheduler(items, settings, audit)
        scheduler.applicationReady()
        scheduler.maybeReindexVectors()
        scheduler.maybeReindexVectors()
        verify(items, times(1)).reindexShared()
        verify(settings).recordReindexRun(1, 2, "scheduler", 0)
    }
    @Test
    fun `scheduled indexing waits for startup migrations and seed runners`() {
        val items = mock(ItemService::class.java)
        val settings = mock(AdminSettingsService::class.java)
        val audit = mock(AuditLogService::class.java)
        val scheduler = VectorReindexScheduler(items, settings, audit)
        scheduler.maybeReindexVectors()
        verifyNoInteractions(items, settings, audit)
        `when`(settings.currentEntity()).thenReturn(AppSettings(vectorReindexEnabled = false))
        scheduler.applicationReady()
        scheduler.maybeReindexVectors()
        verify(settings).currentEntity()
        verifyNoInteractions(items, audit)
    }
}
