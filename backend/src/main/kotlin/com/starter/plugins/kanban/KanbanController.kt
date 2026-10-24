package com.starter.plugins.kanban

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/plugins/kanban/boards")
class KanbanController(private val service: KanbanService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) projectId: UUID?) = service.list(user.username, projectId)
    @GetMapping("/{id}") fun get(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.get(user.username, id)
    @GetMapping("/{id}/members") fun members(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = service.members(user.username, id)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @RequestBody request: KanbanRequest) = service.save(user.username, null, request)
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody request: KanbanRequest) = service.save(user.username, id, request)
    @PostMapping("/{id}/cards/{cardId}/move") fun move(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable cardId: UUID, @RequestBody request: MoveTicketRequest) = service.move(user.username, id, cardId, request)
    @PostMapping("/{id}/sprints/{sprintId}/start") fun startSprint(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable sprintId: UUID, @RequestBody request: SprintActionRequest) = service.startSprint(user.username, id, sprintId, request)
    @PostMapping("/{id}/sprints/{sprintId}/complete") fun completeSprint(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable sprintId: UUID, @RequestBody request: SprintActionRequest) = service.completeSprint(user.username, id, sprintId, request)
    @PostMapping("/{id}/sprints/{sprintId}/delete") fun deleteSprint(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable sprintId: UUID, @RequestBody request: DeleteTicketRequest) = service.deleteSprint(user.username, id, sprintId, request)
    @PostMapping("/{id}/cards/{cardId}/delete") fun deleteTicket(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable cardId: UUID, @RequestBody request: DeleteTicketRequest) = service.deleteTicket(user.username, id, cardId, request)
}

@RestController @RequestMapping("/api/plugins/kanban/permissions")
class KanbanPermissionsController(private val service: KanbanService) {
    @GetMapping fun permissions(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) projectId: UUID?) = service.creationPermissions(user.username, projectId)
}
