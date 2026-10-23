package com.starter.controller

import com.starter.dto.DocumentAccessRequest
import com.starter.service.DocumentAccessService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class DocumentAccessController(private val service: DocumentAccessService) {
    @GetMapping("/api/document-access") fun policy() = service.current()
}

@RestController
@RequestMapping("/api/admin/document-access")
@PreAuthorize("hasRole('ADMIN')")
class AdminDocumentAccessController(private val service: DocumentAccessService) {
    @GetMapping fun settings() = service.current()
    @PutMapping fun save(@Valid @RequestBody request: DocumentAccessRequest, @AuthenticationPrincipal user: UserDetails) = service.save(request, user.username)
}
