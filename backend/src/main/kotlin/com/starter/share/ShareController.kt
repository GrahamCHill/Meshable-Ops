package com.starter.share

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/admin/shares") @PreAuthorize("hasRole('ADMIN')")
class AdminShareController(private val service: FileShareService, private val settings: ShareSettingsService) {
    @GetMapping fun list() = service.list()
    @GetMapping("/settings") fun settings() = settings.settings()
    @PutMapping("/settings") fun settings(@Valid @RequestBody request: ShareSettingsRequest) = settings.save(request)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody request: CreateShareRequest) = service.create(user.username, request)
    @PostMapping("/{id}/revoke") fun revoke(@PathVariable id: UUID): com.starter.dto.MessageResponse { service.revoke(id); return com.starter.dto.MessageResponse("Share revoked") }
}
@RestController @RequestMapping("/api/shared/{token}")
class PublicShareController(private val service: FileShareService, private val rates: com.starter.security.RateLimits) {
    @PostMapping("/unlock") fun unlock(@PathVariable token: String, @Valid @RequestBody body: SharePasswordRequest, request: HttpServletRequest): ResponseEntity<ShareUnlocked> {
        rates.check("share-unlock-ip", request.remoteAddr, 30, 300)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.unlock(token, body.password, request.remoteAddr))
    }
    @GetMapping fun metadata(@PathVariable token: String, @RequestHeader("X-Share-Access") access: String) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.metadata(token, access))
    @GetMapping("/pages/{page}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun page(@PathVariable token: String, @RequestHeader("X-Share-Access") access: String, @PathVariable page: Int, request: HttpServletRequest): ResponseEntity<ByteArray> {
        rates.check("share-preview-ip", request.remoteAddr, 120, 300)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").contentType(MediaType.IMAGE_PNG).body(service.page(token, access, page))
    }
    @PostMapping("/download")
    fun download(@PathVariable token: String, @RequestHeader("X-Share-Access") access: String, @Valid @RequestBody body: SharePasswordRequest, request: HttpServletRequest): ResponseEntity<ByteArrayResource> {
        val file = service.download(token, access, body.password, request.remoteAddr)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename, Charsets.UTF_8).build().toString()).contentType(MediaType.APPLICATION_PDF).body(ByteArrayResource(file.bytes))
    }
}
