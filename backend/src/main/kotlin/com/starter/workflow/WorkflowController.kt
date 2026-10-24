package com.starter.workflow

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/workflows")
class WorkflowController(private val service: WorkflowService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails) = service.list(user.username)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody request: WorkflowRequest) = service.save(user.username, null, request)
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @Valid @RequestBody request: WorkflowRequest) = service.save(user.username, id, request)
}
