package com.starter.plugins.calendar

import com.starter.plugins.integrations.IntegrationUnavailable
import com.starter.security.RateLimits
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Component
class PersonalCalendarSyncScheduler(private val connections: PersonalCalendarRepository,private val engine: PersonalCalendarSyncEngine) {
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    fun tick() {
        connections.due(Instant.now(),PageRequest.of(0,20)).forEach { id ->
            try {engine.sync(id)} catch (_:Exception) { runCatching { engine.failed(id) } }
        }
    }
}

@RestController @RequestMapping("/api/plugins/calendar/personal")
class PersonalCalendarController(private val settings: PersonalCalendarSettingsService,private val engine: PersonalCalendarSyncEngine,private val rates: RateLimits) {
    @GetMapping fun settings(@AuthenticationPrincipal user: UserDetails) = settings.settings(user.username)
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails,@RequestBody request: PersonalCalendarRequest) = settings.save(user.username,request)
    @DeleteMapping fun disconnect(@AuthenticationPrincipal user: UserDetails,@RequestParam revision: Long) = settings.disconnect(user.username,revision)
    @PostMapping("/sync") fun sync(@AuthenticationPrincipal user: UserDetails): PersonalCalendarSettings {
        rates.check("personal-calendar-sync",user.username,5,60)
        val row=settings.owned(user.username)
        if (!row.enabled) throw com.starter.exception.BadRequestException("Enable your personal connection before syncing")
        try {engine.sync(row.id)} catch (_:Exception) {engine.failed(row.id);throw IntegrationUnavailable("Calendar sync could not finish. Check your credentials and permissions; local events remain available.")}
        return settings.settings(user.username)
    }
}
