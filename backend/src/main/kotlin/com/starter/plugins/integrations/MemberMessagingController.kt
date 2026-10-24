package com.starter.plugins.integrations

import com.starter.security.RateLimits
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @PreAuthorize("hasRole('ADMIN')")
class MemberMessagingController(private val messaging: MemberMessaging, private val rates: RateLimits) {
    @GetMapping("/api/admin/plugins/{id:discord|telegram|teams}/members") fun members(@PathVariable id: String) = messaging.identities(id)
    @GetMapping("/api/admin/plugins/{id:discord|telegram|teams}/member-options") fun options() = messaging.options()
    @PutMapping("/api/admin/plugins/{id:discord|telegram|teams}/members/{userId}") fun save(@PathVariable id: String, @PathVariable userId: UUID, @RequestBody input: MemberIdentityInput) = messaging.save(id, userId, input)
    @DeleteMapping("/api/admin/plugins/{id:discord|telegram|teams}/members/{userId}") fun remove(@PathVariable id: String, @PathVariable userId: UUID, @RequestParam revision: Long) = messaging.remove(id, userId, revision)
    @GetMapping("/api/plugins/{id:discord|telegram|teams}/destinations") fun destinations(@PathVariable id: String) = messaging.destinations(id)
    @PostMapping("/api/plugins/{id:discord|telegram|teams}/post") fun post(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: String, @RequestBody input: ChannelPostInput): com.starter.dto.EmailDeliveryResult {
        rates.check("plugin-post-$id", actor.username, 10, 60); return messaging.post(id, input)
    }
}
