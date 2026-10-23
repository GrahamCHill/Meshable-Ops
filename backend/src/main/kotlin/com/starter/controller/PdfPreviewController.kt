package com.starter.controller

import com.starter.service.DocumentDeliveryService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/items/{id}/pdf")
class PdfPreviewController(private val delivery: DocumentDeliveryService) {
    @GetMapping fun preview(@PathVariable id: UUID, @RequestParam path: String) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(delivery.preview(id, path))

    @GetMapping("/pages/{page}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun page(@PathVariable id: UUID, @RequestParam path: String, @PathVariable page: Int, @AuthenticationPrincipal user: UserDetails): ResponseEntity<ByteArray> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.IMAGE_PNG).body(delivery.page(id, path, page, user.username))
}
