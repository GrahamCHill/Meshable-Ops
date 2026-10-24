package com.starter.navigation

import com.starter.plugins.PluginActor
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class NavigationController(private val service: NavigationService, private val actors: PluginActor) {
    @GetMapping("/api/navigation")
    fun current(@AuthenticationPrincipal principal: UserDetails) = service.member(actors.current(principal.username))

    @GetMapping("/api/admin/navigation") @PreAuthorize("hasRole('ADMIN')")
    fun admin() = service.admin()

    @PutMapping("/api/admin/navigation") @PreAuthorize("hasRole('ADMIN')")
    fun save(@RequestBody request: NavigationPreferences) = service.save(request)
}
