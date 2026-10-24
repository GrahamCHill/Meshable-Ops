package com.starter.plugins.kanban

import com.starter.security.RateLimits
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/plugins/kanban/boards/{boardId}/development-links")
class KanbanDevelopmentController(private val service: KanbanDevelopmentService, private val rates: RateLimits) {
    @GetMapping fun list(@PathVariable boardId: UUID, @AuthenticationPrincipal user: UserDetails) = service.list(user.username, boardId)
    @PostMapping fun add(@PathVariable boardId: UUID, @RequestBody request: BoardLinkRequest, @AuthenticationPrincipal user: UserDetails): BoardLinkView {
        rates.check("board-development", user.username, 30, 60); return service.add(user.username, boardId, request)
    }
    @PostMapping("/{linkId}/refresh") fun refresh(@PathVariable boardId: UUID, @PathVariable linkId: UUID, @AuthenticationPrincipal user: UserDetails): BoardLinkView {
        rates.check("board-development", user.username, 30, 60); return service.refresh(user.username, boardId, linkId)
    }
    @DeleteMapping("/{linkId}") fun remove(@PathVariable boardId: UUID, @PathVariable linkId: UUID, @AuthenticationPrincipal user: UserDetails) = service.remove(user.username, boardId, linkId)
}
