package com.starter.controller

import com.starter.service.storage.StorageObjectUpload
import com.starter.dto.*
import com.starter.exception.BadRequestException
import com.starter.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.core.io.ByteArrayResource
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/items")
class ItemController(
    private val itemService: ItemService,
    private val adminSettingsService: AdminSettingsService,
    private val auditLogService: AuditLogService,
) {

    @GetMapping("/{id}") fun get(@PathVariable id: UUID) = itemService.getShared(id)

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) connectionId: UUID?,
    ) = itemService.listShared(page, size, connectionId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@AuthenticationPrincipal user: UserDetails, @Valid @RequestBody req: ItemRequest) =
        itemService.create(user.username, req)

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody req: ItemRequest
    ) = itemService.update(user.username, id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@AuthenticationPrincipal user: UserDetails, @PathVariable id: UUID) =
        itemService.delete(user.username, id)

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    fun reindex(
        @AuthenticationPrincipal user: UserDetails,
        request: HttpServletRequest
    ): MessageResponse {
        val result = itemService.reindexShared()
        adminSettingsService.recordReindexRun(result.folders, result.documents, "manual", result.failures)
        auditLogService.record(
            actingEmail = user.username,
            action = "VECTOR_REINDEX_MANUAL",
            resourceType = "FOLDER",
            resourceId = "${result.folders}:${result.documents}",
            ipAddress = request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
        )
        return MessageResponse("Reindexed ${result.folders} folders and ${result.documents} documents; ${result.contentDocuments} with searchable content, ${result.failures} indexing errors. Unsupported or oversized files retain metadata search.")
    }

    @GetMapping("/{id}/contents")
    fun contents(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "") path: String,
    ) = itemService.browse(id, path)

    @PostMapping("/{id}/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "") path: String,
        @RequestParam("files") files: List<MultipartFile>,
        @RequestParam("paths") paths: List<String>,
    ): StorageContentsResponse {
        if (files.size != paths.size) throw BadRequestException("Each uploaded file must have a path")
        val uploads = files.zip(paths).map { (file, relativePath) ->
            StorageObjectUpload(relativePath, file.contentType, file.bytes)
        }
        return itemService.upload(user.username, id, path, uploads)
    }

    @GetMapping("/{id}/download")
    fun download(
        @PathVariable id: UUID,
        @RequestParam path: String,
        @RequestParam(defaultValue = "false") archive: Boolean,
    ): ResponseEntity<ByteArrayResource> {
        val download = itemService.download(id, path, archive)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename).build().toString())
            .contentType(MediaType.parseMediaType(download.contentType))
            .contentLength(download.bytes.size.toLong())
            .body(ByteArrayResource(download.bytes))
    }
}
