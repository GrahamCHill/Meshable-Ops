package com.starter.plugins.calendar

import com.starter.security.RateLimits
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController @RequestMapping("/api/plugins/calendar")
class CalendarController(private val local: CalendarService, private val connections: CalendarConnections, private val rates: RateLimits) {
    @GetMapping("/events") fun events(@AuthenticationPrincipal user: UserDetails, @RequestParam from: Instant, @RequestParam until: Instant,@RequestParam(defaultValue="UTC") zone: String) = local.window(user.username,from,until,parseZone(zone))
    @PostMapping("/events") fun create(@AuthenticationPrincipal user: UserDetails, @RequestBody request: CalendarEventRequest) = local.save(user.username,null,request)
    @PutMapping("/events/{id}") fun update(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody request: CalendarEventRequest) = local.save(user.username,id,request)
    @DeleteMapping("/events/{id}") fun delete(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestParam revision: Long) = local.delete(user.username,id,revision)
    @GetMapping("/export") fun export(@AuthenticationPrincipal user: UserDetails, @RequestParam from: Instant, @RequestParam until: Instant,@RequestParam(defaultValue="UTC") zone: String): ResponseEntity<String> {
        val window = local.window(user.username,from,until,parseZone(zone))
        // Do not silently export an incomplete window.
        if (window.truncated) throw com.starter.exception.BadRequestException("Choose a smaller date range before exporting")
        return ResponseEntity.ok().header("Content-Type","text/calendar; charset=utf-8").header("Content-Disposition","attachment; filename=calendar.ics")
            .body(IcalendarCodec.export(window.events))
    }
    private fun parseZone(value: String) = runCatching { java.time.ZoneId.of(value) }.getOrElse { throw com.starter.exception.BadRequestException("Choose a valid calendar time zone") }
    @GetMapping("/providers/{provider}/events") fun connected(@AuthenticationPrincipal user: UserDetails, @PathVariable provider: String, @RequestParam from: Instant, @RequestParam until: Instant): CalendarWindow {
        rates.check("calendar-read-$provider",user.username,20,60)
        return connections.window(user.username,provider,from,until)
    }
    @PostMapping("/events/{id}/publish/{provider}") fun publish(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable provider: String): CalendarPublication {
        rates.check("calendar-publish-$provider",user.username,10,60)
        return connections.publish(user.username,provider,id)
    }
}
