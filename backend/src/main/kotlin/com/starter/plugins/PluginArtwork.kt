package com.starter.plugins

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.NotFoundException
import com.starter.plugins.packages.InstalledPluginCatalogue
import com.starter.service.branding.*
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.http.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class PluginArtwork(private val registry: PluginRegistry, private val packages: InstalledPluginCatalogue, private val mapper: ObjectMapper) {
    fun read(id: String, kind: String): Pair<ByteArray, MediaType> {
        if (kind !in setOf("icon", "banner")) throw NotFoundException("Artwork unavailable")
        val manifest = registry.definition(id)
        val record = packages.find(id)
        if (record != null) {
            val encoded = mapper.readValue(record.filesJson, object : TypeReference<Map<String,String>>() {})["__$kind"] ?: throw NotFoundException("Artwork unavailable")
            return java.util.Base64.getDecoder().decode(encoded) to if (kind == "icon") MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG
        }
        val path = if (kind == "icon") manifest.icon else manifest.banner
        if (path != null && path != "builtin") {
            val bytes = javaClass.getResourceAsStream("/plugins/$id/$path")?.use { it.readNBytes(2 * 1024 * 1024 + 1) } ?: throw NotFoundException("Artwork unavailable")
            return BrandingImages.normalize(bytes, if (kind == "icon") BrandingImageProfile.FAVICON else BrandingImageProfile.LOGIN) to if (kind == "icon") MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG
        }
        val image = BufferedImage(if (kind == "icon") 64 else 1200, if (kind == "icon") 64 else 240, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try { g.color = Color(36 + (id.hashCode() ushr 16 and 63), 70 + (id.hashCode() ushr 8 and 63), 88 + (id.hashCode() and 63)); g.fillRect(0,0,image.width,image.height)
            if (kind == "icon") {g.color = Color.WHITE; g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 28); g.drawString(manifest.name.take(1).uppercase(),22,42)}
        } finally {g.dispose()}
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray() to MediaType.IMAGE_PNG
    }
}
@RestController
class PluginArtworkController(private val artwork: PluginArtwork, private val registry: PluginRegistry, private val actors: PluginActor) {
    @GetMapping("/api/plugins/{id}/assets/{kind}") fun member(@PathVariable id: String, @PathVariable kind: String, @AuthenticationPrincipal user: UserDetails): ResponseEntity<ByteArray> {
        registry.requireAvailable(id, actors.current(user.username).role); return response(id,kind)
    }
    @GetMapping("/api/admin/plugins/{id}/assets/{kind}") @PreAuthorize("hasRole('ADMIN')") fun admin(@PathVariable id: String, @PathVariable kind: String) = response(id,kind)
    private fun response(id: String, kind: String): ResponseEntity<ByteArray> { val (bytes,type) = artwork.read(id,kind); return ResponseEntity.ok().contentType(type).header(HttpHeaders.CACHE_CONTROL,"private, max-age=300").header("X-Content-Type-Options","nosniff").body(bytes) }
}
