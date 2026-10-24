package com.starter.projects

import com.starter.service.*
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController @RequestMapping("/api/projects/clients/{id}/logo")
class ClientLogoController(private val clients: ProjectService, private val artwork: WorkspaceArtworkService) {
    @GetMapping fun current(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): ArtworkView {
        clients.requireClient(user.username, id); return artwork.current("client:$id", "building")
    }
    @PutMapping fun preset(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestBody input: ArtworkInput): ArtworkView {
        clients.requireClient(user.username, id, true); return artwork.preset("client:$id", input)
    }
    @PostMapping("/image", consumes=[MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam revision: Long, @RequestParam file: MultipartFile): ArtworkView {
        clients.requireClient(user.username, id, true); return artwork.upload("client:$id", revision, file)
    }
    @GetMapping("/image/{version}") fun image(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @PathVariable version: String): ResponseEntity<ByteArray> {
        clients.requireClient(user.username, id)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options", "nosniff").body(artwork.image("client:$id", version))
    }
}
