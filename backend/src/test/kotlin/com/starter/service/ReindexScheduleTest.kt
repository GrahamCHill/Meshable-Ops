package com.starter.service

import com.starter.dto.AdminSettingsRequest
import com.starter.model.postgres.AppSettings
import com.starter.repository.postgres.AppSettingsRepository
import com.starter.security.testEncryption
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant

class ReindexScheduleTest {
    private val daily = AppSettings(vectorReindexEnabled = true, vectorReindexSchedule = "DAILY", vectorReindexTimeZone = "Africa/Johannesburg")

    @Test fun `daily midnight follows the selected timezone and advances after a completed midnight`() {
        assertThat(ReindexSchedule.next(daily, Instant.parse("2026-10-23T20:00:00Z"))).isEqualTo(Instant.parse("2026-10-23T22:00:00Z"))
        assertThat(ReindexSchedule.next(daily, Instant.parse("2026-10-23T22:00:00Z"))).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"))
    }
    @Test fun `weekly Friday midnight skips a Friday whose scheduled time has passed`() {
        daily.vectorReindexSchedule = "WEEKLY"
        assertThat(ReindexSchedule.next(daily, Instant.parse("2026-10-22T12:00:00Z"))).isEqualTo(Instant.parse("2026-10-22T22:00:00Z"))
        assertThat(ReindexSchedule.next(daily, Instant.parse("2026-10-23T12:00:00Z"))).isEqualTo(Instant.parse("2026-10-29T22:00:00Z"))
    }
    @Test fun `calendar jobs account for daylight saving and disabled jobs have no next run`() {
        daily.vectorReindexTimeZone = "America/New_York"
        assertThat(ReindexSchedule.next(daily, Instant.parse("2026-11-01T04:00:00Z"))).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"))
        daily.vectorReindexEnabled = false
        assertThat(ReindexSchedule.next(daily, Instant.now())).isNull()
    }
    @Test fun `invalid schedule fields are rejected`() {
        listOf(arrayOf("INVALID", "00:00", "FRIDAY", "UTC"), arrayOf("DAILY", "25:00", "FRIDAY", "UTC"),
            arrayOf("WEEKLY", "00:00", "NO_DAY", "UTC"), arrayOf("DAILY", "00:00", "FRIDAY", "No/Zone")).forEach { values ->
            assertThatThrownBy { ReindexSchedule.validate(values[0], values[1], values[2], values[3]) }.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        }
    }
    @Test fun `changed files queue one persistent run at the next midnight without postponing it every check`() {
        val repository = mock(AppSettingsRepository::class.java)
        val saved = daily.copy(vectorReindexSchedule = "ON_CHANGE")
        `when`(repository.findTopByOrderByCreatedAtAsc()).thenReturn(saved)
        `when`(repository.save(any(AppSettings::class.java))).thenAnswer { it.arguments[0] }
        val service = AdminSettingsService(repository, testEncryption())
        service.recordChangeCheck(false, Instant.parse("2026-10-23T18:00:00Z"))
        assertThat(saved.vectorReindexNextRunAt).isNull()
        service.recordChangeCheck(true, Instant.parse("2026-10-23T20:00:00Z"))
        assertThat(saved.vectorReindexPendingChanges).isTrue()
        assertThat(saved.vectorReindexNextRunAt).isEqualTo(Instant.parse("2026-10-23T22:00:00Z"))
        service.recordChangeCheck(true, Instant.parse("2026-10-23T21:00:00Z"))
        assertThat(saved.vectorReindexNextRunAt).isEqualTo(Instant.parse("2026-10-23T22:00:00Z"))
        service.recordReindexRun(1, 2, "scheduler")
        assertThat(saved.vectorReindexPendingChanges).isFalse()
        assertThat(saved.vectorReindexNextRunAt).isNull()
    }
    @Test fun `saved weekly schedule has a next run and disabling it clears pending work`() {
        val repository = mock(AppSettingsRepository::class.java)
        val saved = AppSettings()
        `when`(repository.findTopByOrderByCreatedAtAsc()).thenReturn(saved)
        `when`(repository.save(any(AppSettings::class.java))).thenAnswer { it.arguments[0] }
        val service = AdminSettingsService(repository, testEncryption())
        val request = AdminSettingsRequest(vectorReindexEnabled = true, vectorReindexSchedule = "WEEKLY")
        val response = service.updateSettings(request)
        assertThat(response.vectorReindexNextRunAt).isNotNull()
        assertThat(response.vectorReindexSchedule).isEqualTo("WEEKLY")
        service.updateSettings(request.copy(vectorReindexEnabled = false))
        assertThat(saved.vectorReindexNextRunAt).isNull()
        assertThat(saved.vectorReindexPendingChanges).isFalse()
    }
}
