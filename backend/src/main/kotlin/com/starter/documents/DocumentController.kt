package com.starter.documents

import jakarta.validation.Valid
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/admin/document-templates") @PreAuthorize("hasRole('ADMIN')")
class AdminDocumentTemplateController(private val service: DocumentTemplateService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails) = service.list(user.username, true)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody request: TemplateRequest) = service.save(user.username, null, request)
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @Valid @RequestBody request: TemplateRequest) = service.save(user.username, id, request)
}
@RestController @RequestMapping("/api/documents")
class GeneratedDocumentController(private val service: GeneratedDocumentService, private val templates: DocumentTemplateService) {
    @GetMapping("/templates") fun templates(@AuthenticationPrincipal user: UserDetails) = templates.list(user.username)
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails) = service.list(user.username)
    @PostMapping fun generate(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody request: GenerateDocumentRequest) = service.generate(user.username, request)
    @GetMapping("/{id}/pages/{page}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun page(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @PathVariable page: Int) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(service.page(user.username, id, page))
    @GetMapping("/{id}/download") fun download(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID): ResponseEntity<ByteArrayResource> {
        val file = service.download(user.username, id)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename, Charsets.UTF_8).build().toString()).contentType(MediaType.APPLICATION_PDF).body(ByteArrayResource(file.bytes))
    }
    @PostMapping("/{id}/email") fun email(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID, @Valid @RequestBody request: EmailDocumentRequest): com.starter.dto.EmailDeliveryResult {
        val result = service.email(user.username, id, request)
        if (result.status != "ACCEPTED") throw com.starter.email.EmailDeliveryException(result.message)
        return result
    }
}
