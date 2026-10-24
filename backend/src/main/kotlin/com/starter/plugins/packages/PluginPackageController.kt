package com.starter.plugins.packages

import com.starter.plugins.PluginActor
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
class PluginPackageController(private val packages: PluginPackageService, private val actors: PluginActor) {
    @GetMapping("/api/admin/security/extensions") @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun security() = packages.security()
    @PutMapping("/api/admin/security/extensions") @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun saveSecurity(@RequestBody request: ExtensionSecurityView) = packages.saveSecurity(request)
    @PostMapping("/api/admin/plugins/install", consumes = ["multipart/form-data"]) @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun install(@RequestParam("file") file: MultipartFile) = packages.install(file)
    @GetMapping("/api/plugins/{id}/runtime")
    fun runtime(@PathVariable id: String, @RequestParam(defaultValue = "") section: String, @AuthenticationPrincipal user: UserDetails) = packages.runtime(id, section, actors.current(user.username).role)
}
