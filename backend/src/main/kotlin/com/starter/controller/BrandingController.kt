package com.starter.controller

import com.starter.model.postgres.BrandingKind
import com.starter.service.branding.WorkspaceBrandingService
import com.starter.dto.BrandingPreferences
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Duration

@RestController
class BrandingController(private val service: WorkspaceBrandingService) {
    @GetMapping("/api/branding")
    fun current() = ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.current())

    @GetMapping("/api/branding/assets/{kind}/{version}")
    fun image(@PathVariable kind: BrandingKind, @PathVariable version: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
            .body(service.asset(kind, version))

    @GetMapping("/api/branding/login-images/{slot}/{version}")
    fun background(@PathVariable slot: Int, @PathVariable version: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
            .body(service.background(slot, version))
}

@RestController
@RequestMapping("/api/admin/branding")
@PreAuthorize("hasRole('ADMIN')")
class AdminBrandingController(private val service: WorkspaceBrandingService) {
    @PutMapping
    fun save(@RequestBody request: BrandingPreferences) = service.save(request)

    @PostMapping("/login-images/{slot}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadBackground(@PathVariable slot: Int, @RequestParam("file") file: MultipartFile) = service.uploadBackground(slot, file)

    @DeleteMapping("/login-images/{slot}")
    fun removeBackground(@PathVariable slot: Int) = service.removeBackground(slot)

    @PostMapping("/{kind}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@PathVariable kind: BrandingKind, @RequestParam("file") file: MultipartFile) = service.upload(kind, file)

    @DeleteMapping("/{kind}")
    fun reset(@PathVariable kind: BrandingKind) = service.reset(kind)
}
