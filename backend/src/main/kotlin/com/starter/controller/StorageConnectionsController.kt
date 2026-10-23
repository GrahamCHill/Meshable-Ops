package com.starter.controller

import com.starter.dto.*
import com.starter.service.StorageConnectionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminStorageConnectionsController(private val service: StorageConnectionService) {
    @GetMapping("/storage-connections") fun list() = service.listAdmin()
    @PostMapping("/storage-connections")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: StorageConnectionRequest, @AuthenticationPrincipal user: UserDetails) = service.save(null, req, user.username)
    @PutMapping("/storage-connections/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody req: StorageConnectionRequest, @AuthenticationPrincipal user: UserDetails) = service.save(id, req, user.username)
    @PostMapping("/storage-connections/{id}/test") fun test(@PathVariable id: UUID) = service.test(id)
    @GetMapping("/storage-connections/{id}/browse")
    fun browse(@PathVariable id: UUID, @RequestParam(defaultValue = "") path: String) = service.browse(id, path)
    @PostMapping("/storage-connections/{id}/folders")
    fun share(@PathVariable id: UUID, @Valid @RequestBody req: ShareFolderRequest, @AuthenticationPrincipal user: UserDetails) = service.shareFolder(id, req, user.username)
    @DeleteMapping("/storage-connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails) = service.remove(id, user.username)
    @GetMapping("/storage-folders") fun legacyFolders() = service.legacyFolders()
    @PatchMapping("/storage-folders/{id}")
    fun editFolder(@PathVariable id: UUID, @Valid @RequestBody req: UpdateSharedFolderRequest, @AuthenticationPrincipal user: UserDetails) = service.updateFolder(id, req, user.username)
    @DeleteMapping("/storage-folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFolder(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails) = service.removeFolder(id, user.username)
}

@RestController
@RequestMapping("/api/storage-connections")
class SharedStorageConnectionsController(private val service: StorageConnectionService) {
    @GetMapping fun list() = service.listShared()
}
