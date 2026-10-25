package com.starter.controller

import com.starter.service.GeneratedPdfCleanupService
import com.starter.service.PdfCleanupInput
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/storage-pdf-cleanup")
@PreAuthorize("hasRole('ADMIN')")
class GeneratedPdfCleanupController(private val service: GeneratedPdfCleanupService) {
    @GetMapping fun settings() = service.view()
    @PutMapping fun update(@AuthenticationPrincipal actor: UserDetails, @RequestBody input: PdfCleanupInput) = service.update(input, actor.username)
    @PostMapping("/clear") fun clear(@AuthenticationPrincipal actor: UserDetails) = service.clearExpired(actor.username)
}
