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

@RestController @RequestMapping("/api/projects/{id}/icon")
class ProjectArtworkController(private val access:ProjectAccess,private val artwork:WorkspaceArtworkService) {
    private fun require(id:UUID,email:String,write:Boolean=false) {access.require(id,access.actor(email),write)}
    @GetMapping fun current(@PathVariable id:UUID,@AuthenticationPrincipal user:UserDetails):ArtworkView {require(id,user.username);return artwork.current("project:$id")}
    @PutMapping fun preset(@PathVariable id:UUID,@AuthenticationPrincipal user:UserDetails,@RequestBody input:ArtworkInput):ArtworkView {require(id,user.username,true);return artwork.preset("project:$id",input)}
    @PostMapping("/image",consumes=[MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@PathVariable id:UUID,@AuthenticationPrincipal user:UserDetails,@RequestParam revision:Long,@RequestParam file:MultipartFile):ArtworkView {require(id,user.username,true);return artwork.upload("project:$id",revision,file)}
    @GetMapping("/image/{version}") fun image(@PathVariable id:UUID,@AuthenticationPrincipal user:UserDetails,@PathVariable version:String):ResponseEntity<ByteArray> {
        require(id,user.username)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(artwork.image("project:$id",version))
    }
}
