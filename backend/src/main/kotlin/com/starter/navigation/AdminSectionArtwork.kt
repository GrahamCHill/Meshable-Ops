package com.starter.navigation

import com.starter.exception.NotFoundException
import com.starter.plugins.PluginRegistry
import com.starter.service.*
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

data class AdminSection(val id:String,val label:String,val defaultIcon:String,val artwork:ArtworkView)
@Component
class AdminSectionCatalogue(private val registry:PluginRegistry,private val artwork:WorkspaceArtworkService) {
    fun all():List<AdminSection> {
        val core=listOf(Triple("workspace","Workspace","layout"),Triple("people-security","People & security","users"),
            Triple("storage-sharing","Storage & sharing","cloud"),Triple("communications","Communications","mail"),
            Triple("documents-workflows","Documents & workflows","workflow"),Triple("analytics-search","Analytics & search","chart"),Triple("operations","Operations","activity"))
        val plugins=registry.installed().filter {!it.removed}.mapNotNull {it.manifest.adminMenu}.map {Triple(it.category,it.categoryLabel.ifEmpty {it.category},it.icon?.takeIf {name->name in WorkspaceArtworkService.ICONS} ?: "plug")}
        return (core+plugins).distinctBy {it.first}.map {(id,label,icon)->AdminSection(id,label,icon,artwork.current("admin:$id",icon))}
    }
    fun require(id:String)=all().firstOrNull {it.id==id} ?: throw NotFoundException("Administration section unavailable")
}

@RestController @RequestMapping("/api/navigation/admin-sections") @PreAuthorize("hasRole('ADMIN')")
class AdminSectionPresentationController(private val sections:AdminSectionCatalogue,private val artwork:WorkspaceArtworkService) {
    @GetMapping fun icons()=sections.all().associate {it.id to it.artwork}
    @GetMapping("/{id}/image/{version}") fun image(@PathVariable id:String,@PathVariable version:String):ResponseEntity<ByteArray> {
        sections.require(id)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(artwork.image("admin:$id",version))
    }
}

@RestController @RequestMapping("/api/admin/navigation/sections") @PreAuthorize("hasRole('ADMIN')")
class AdminSectionArtworkController(private val sections:AdminSectionCatalogue,private val artwork:WorkspaceArtworkService) {
    @GetMapping fun list()=sections.all()
    @PutMapping("/{id}") fun preset(@PathVariable id:String,@RequestBody input:ArtworkInput):ArtworkView {sections.require(id);return artwork.preset("admin:$id",input)}
    @PostMapping("/{id}/image",consumes=[MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@PathVariable id:String,@RequestParam revision:Long,@RequestParam file:MultipartFile):ArtworkView {sections.require(id);return artwork.upload("admin:$id",revision,file)}
    @GetMapping("/{id}/image/{version}") fun image(@PathVariable id:String,@PathVariable version:String):ResponseEntity<ByteArray> {
        sections.require(id)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(artwork.image("admin:$id",version))
    }
}
