package com.starter.plugins.kanban

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/plugins/kanban/boards")
class KanbanController(private val service: KanbanService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails) = service.list(user.username)
    @GetMapping("/{id}") fun get(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.get(user.username, id)
    @GetMapping("/{id}/members") fun members(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.members(user.username, id)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @RequestBody request: KanbanRequest) = service.save(user.username, null, request)
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody request: KanbanRequest) = service.save(user.username, id, request)
}
