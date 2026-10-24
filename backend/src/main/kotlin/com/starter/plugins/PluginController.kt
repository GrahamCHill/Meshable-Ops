package com.starter.plugins

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class PluginController(private val registry: PluginRegistry, private val actors: PluginActor) {
    @GetMapping("/api/plugins")
    fun available(@AuthenticationPrincipal user: UserDetails) = registry.available(actors.current(user.username).role)
}

@RestController @RequestMapping("/api/admin/plugins") @PreAuthorize("hasRole('ADMIN')")
class AdminPluginController(private val registry: PluginRegistry) {
    @GetMapping fun installed() = registry.installed()
    @PutMapping("/{id}") fun configure(@PathVariable id: String, @RequestBody request: PluginSettingsRequest) = registry.configure(id, request)
}

@RestController @RequestMapping("/api/admin/plugins") @PreAuthorize("hasRole('ADMIN')")
class AdminPluginDataController(private val data: PluginDataService) {
    @PostMapping("/{id}/clear-data") fun clear(@PathVariable id: String, @RequestBody request: PluginClearRequest) = data.clear(id, request)
}
