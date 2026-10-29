package com.starter.plugins.workflows

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/admin/workflows") @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
class WorkflowController(private val service: WorkflowService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails) = service.list(user.username)
    @GetMapping("/reviewers") fun reviewers(@AuthenticationPrincipal user: UserDetails) = service.reviewers(user.username)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody request: WorkflowRequest) = service.save(user.username, null, request)
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @Valid @RequestBody request: WorkflowRequest) = service.save(user.username, id, request)
}

/** Keep write-only legacy clients working while enforcing the same administrator gate. */
@RestController @RequestMapping("/api/workflows") @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
class LegacyWorkflowAuthoringController(private val service: WorkflowService) {
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails,@Valid @RequestBody request: WorkflowRequest) = service.save(user.username,null,request)
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@Valid @RequestBody request: WorkflowRequest) = service.save(user.username,id,request)
}
