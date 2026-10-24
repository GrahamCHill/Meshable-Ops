package com.starter.projects

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/projects")
class ProjectController(private val service: ProjectService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails, @RequestParam(defaultValue = "") query: String, @RequestParam(required = false) category: ProjectCategory?, @RequestParam(required = false) tags: Set<UUID>?) = service.list(user.username, query, category, tags ?: emptySet())
    @GetMapping("/people") fun people(@AuthenticationPrincipal user: UserDetails) = service.people(user.username)
    @GetMapping("/options") fun options(@AuthenticationPrincipal user: UserDetails) = service.options(user.username)
    @GetMapping("/clients") fun clients(@AuthenticationPrincipal user: UserDetails) = service.clients(user.username)
    @GetMapping("/clients/{id}") fun client(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.getClient(user.username, id)
    @PostMapping("/clients") fun createClient(@AuthenticationPrincipal user: UserDetails, @RequestBody input: ClientInput) = service.saveClient(user.username, null, input)
    @PutMapping("/clients/{id}") fun updateClient(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody input: ClientInput) = service.saveClient(user.username, id, input)
    @GetMapping("/{id}") fun get(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.get(user.username, id)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @RequestBody input: ProjectInput) = service.save(user.username, null, input)
    @PutMapping("/{id}") fun save(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody input: ProjectInput) = service.save(user.username, id, input)
    @GetMapping("/{id}/members") fun members(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.members(user.username, id)
}

@RestController @RequestMapping("/api/admin/projects") @PreAuthorize("hasRole('ADMIN')")
class ProjectSettingsController(private val settings: ProjectSettingsService) {
    @GetMapping fun get() = settings.current()
    @PutMapping fun save(@RequestBody value: ProjectSettingsView) = settings.save(value)
    @GetMapping("/groups") fun groups() = settings.groups()
    @GetMapping("/people") fun people() = settings.people()
    @PostMapping("/groups") fun create(@RequestBody value: WorkGroupInput) = settings.saveGroup(null, value)
    @PutMapping("/groups/{id}") fun update(@PathVariable id: UUID, @RequestBody value: WorkGroupInput) = settings.saveGroup(id, value)
}
