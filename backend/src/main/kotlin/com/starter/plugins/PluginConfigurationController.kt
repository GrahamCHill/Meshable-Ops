package com.starter.plugins

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/admin/plugins") @PreAuthorize("hasRole('ADMIN')")
class PluginConfigurationController(private val configuration: PluginConfigurationService) {
    @GetMapping("/{id}/configuration") fun view(@PathVariable id: String) = configuration.view(id)
    @PutMapping("/{id}/configuration") fun save(@PathVariable id: String, @RequestBody request: PluginConfigurationRequest) = configuration.save(id, request)
}
