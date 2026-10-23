package com.starter.service

import com.starter.exception.BadRequestException
import com.starter.model.postgres.AppSettings
import java.time.*
import java.time.temporal.TemporalAdjusters

object ReindexSchedule {
    val modes = setOf("INTERVAL", "DAILY", "WEEKLY", "ON_CHANGE")

    fun validate(mode: String, time: String, day: String, zone: String) {
        if (mode !in modes) throw BadRequestException("Choose an interval, daily, weekly, or changed-files schedule")
        if (!Regex("\\d{2}:\\d{2}").matches(time) || runCatching { LocalTime.parse(time) }.isFailure) throw BadRequestException("Enter a valid schedule time as HH:mm")
        if (runCatching { DayOfWeek.valueOf(day) }.isFailure) throw BadRequestException("Choose a valid day of the week")
        if (zone !in ZoneId.getAvailableZoneIds()) throw BadRequestException("Choose a valid IANA timezone")
    }

    fun next(settings: AppSettings, after: Instant): Instant? {
        if (!settings.vectorReindexEnabled) return null
        if (settings.vectorReindexSchedule == "INTERVAL") return after.plusSeconds(settings.vectorReindexIntervalMinutes * 60L)
        val zone = ZoneId.of(settings.vectorReindexTimeZone)
        val time = LocalTime.parse(settings.vectorReindexTime)
        var date = after.atZone(zone).toLocalDate()
        if (settings.vectorReindexSchedule == "WEEKLY") date = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(settings.vectorReindexDayOfWeek)))
        var candidate = date.atTime(time).atZone(zone).toInstant()
        if (!candidate.isAfter(after)) {
            date = if (settings.vectorReindexSchedule == "WEEKLY") date.plusWeeks(1) else date.plusDays(1)
            candidate = date.atTime(time).atZone(zone).toInstant()
        }
        return candidate
    }
}
