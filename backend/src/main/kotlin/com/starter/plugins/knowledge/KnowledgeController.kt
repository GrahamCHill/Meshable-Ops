package com.starter.plugins.knowledge

import com.starter.security.RateLimits
import org.springframework.http.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/plugins/knowledge")
class KnowledgeController(private val pages: KnowledgeService, private val access: KnowledgeAccess,
    private val settings: KnowledgeSettingsService, private val exports: KnowledgeExportService, private val rates: RateLimits) {
    @GetMapping("/status") fun status(@AuthenticationPrincipal user: UserDetails) = access.status(user.username)
    @GetMapping("/pages") fun list(@AuthenticationPrincipal user: UserDetails) = pages.list(user.username)
    @GetMapping("/pages/{id}") fun page(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) = pages.page(user.username, id)
    @PostMapping("/pages") fun create(@AuthenticationPrincipal user: UserDetails, @RequestBody input: KnowledgePageInput): KnowledgePageView {
        rates.check("knowledge-write", user.username, 60, 300); return pages.save(user.username, null, input)
    }
    @PutMapping("/pages/{id}") fun save(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody input: KnowledgePageInput): KnowledgePageView {
        rates.check("knowledge-write", user.username, 60, 300); return pages.save(user.username, id, input)
    }
    @DeleteMapping("/pages/{id}") fun delete(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestParam revision: Long) = pages.delete(user.username, id, revision)
    @PostMapping("/preview/{id}") fun preview(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @RequestBody input: KnowledgePageInput): KnowledgeRendered {
        rates.check("knowledge-preview", user.username, 60, 60); return pages.preview(user.username, id, input)
    }
    @GetMapping("/settings") fun settings(@AuthenticationPrincipal user: UserDetails) = settings.view(user.username)
    @PutMapping("/settings") fun settings(@AuthenticationPrincipal user: UserDetails, @RequestBody input: KnowledgeSettingsInput): KnowledgeSettingsView {
        rates.check("knowledge-settings", user.username, 10, 60); return settings.save(user.username, input)
    }
    @PutMapping("/settings/exporters") fun permissions(@AuthenticationPrincipal user: UserDetails, @RequestBody input: KnowledgePermissionsInput) = settings.permissions(user.username, input)
    @PostMapping("/settings/cover/preview") fun cover(@AuthenticationPrincipal user: UserDetails, @RequestBody input: KnowledgeCoverPreview): ResponseEntity<ByteArray> {
        rates.check("knowledge-settings", user.username, 10, 60); return pdf(settings.preview(user.username, input), "knowledge-cover-preview.pdf")
    }
    @PostMapping("/exports/prepare") fun prepare(@AuthenticationPrincipal user: UserDetails, @RequestBody input: KnowledgeExportInput): List<KnowledgePreparedPage> {
        rates.check("knowledge-prepare", user.username, 30, 3600); return exports.prepare(user.username, input)
    }
    @PostMapping("/exports") fun export(@AuthenticationPrincipal user: UserDetails, @RequestBody input: KnowledgeExportInput): ResponseEntity<ByteArray> {
        rates.check("knowledge-export", user.username, 10, 3600); return pdf(exports.export(user.username, input), "knowledge-book.pdf")
    }
    private fun pdf(bytes: ByteArray, name: String) = ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$name").body(bytes)
}
