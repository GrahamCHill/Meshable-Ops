package com.starter.access

import org.springframework.web.bind.annotation.*

@RestController
class AccessController(private val access: AccessControlService, private val plugins: com.starter.plugins.PluginRegistry) {
    @GetMapping("/api/access/me") fun me() = plugins.effectiveAccess(access.me())
    @GetMapping("/api/admin/access-controls") fun view() = access.view()
    @GetMapping("/api/admin/access-controls/storage-roots") fun roots() = access.roots()
    @PutMapping("/api/admin/access-controls") fun save(@RequestBody request: AccessPolicyRequest) = access.save(request)
}
