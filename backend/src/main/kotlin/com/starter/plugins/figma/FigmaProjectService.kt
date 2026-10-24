package com.starter.plugins.figma

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.*
import com.starter.plugins.PluginConfigurationService
import com.starter.plugins.integrations.*
import com.starter.plugins.projectassets.*
import com.starter.security.RateLimits
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.net.URI
import java.time.Instant
import java.util.UUID

data class FigmaLinkInput(val label: String,val url: String)
data class FigmaMetadata(val name: String,val lastModified: String,val version: String,val editorType: String,val refreshedAt: Instant = Instant.now())
data class FigmaFileView(val id: UUID,val label: String,val url: String,val fileKey: String,val metadata: FigmaMetadata?,val revision: Long)
object FigmaUrls {
    fun key(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val parts = uri?.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (url.length > 2000 || uri?.scheme != "https" || uri.host !in setOf("www.figma.com","figma.com") || uri.userInfo != null || uri.port !in setOf(-1,443) ||
            parts.size < 2 || parts[0] !in setOf("design","file","proto","board","slides") || !parts[1].matches(Regex("[A-Za-z0-9_-]{5,200}")))
            throw BadRequestException("Use an HTTPS Figma design, file, prototype, board or slides URL")
        return parts[1]
    }
}
@Service
class FigmaProjectService(private val assets: ProjectAssetStore,private val configuration: PluginConfigurationService,
    private val http: IntegrationHttpClient,private val mapper: ObjectMapper,private val rates: RateLimits) {
    fun list(email: String,project: UUID) = assets.list(email,"figma",project).map(::view)
    fun link(email: String,project: UUID,input: FigmaLinkInput): FigmaFileView {
        assets.authorize(email,"figma",project,true); FigmaUrls.key(input.url)
        return view(assets.save(email,"figma",project,null,input.label,input.url,"{}",-1))
    }
    fun refresh(email: String,project: UUID,id: UUID,revision: Long): FigmaFileView {
        val actor = assets.authorize(email,"figma",project,true); rates.check("figma-refresh",actor.id.toString(),20,3600)
        val row = assets.get(email,"figma",project,id)
        if (row.revision != revision) throw ConflictException("The Figma link changed. Reload before refreshing.")
        val key = FigmaUrls.key(row.remoteId); val config = configuration.current("figma")
        if (config["accessToken"].isNullOrBlank()) throw BadRequestException("Configure a Figma access token before refreshing metadata")
        if (key !in FigmaPlugin.fileKeys(config["fileKeys"].orEmpty())) throw ForbiddenException("An administrator must approve this file for metadata access")
        val node = http.scoped("GET","https://api.figma.com","v1/files/$key?depth=1",mapOf("X-Figma-Token" to config.getValue("accessToken"))).body
            ?: throw IntegrationUnavailable("Figma returned no file metadata")
        if (configuration.current("figma") != config) throw ConflictException("Figma settings changed. Retry the refresh.")
        val metadata = FigmaMetadata(node.path("name").asText().take(300),node.path("lastModified").asText().take(100),node.path("version").asText().take(100),node.path("editorType").asText().take(40))
        return view(assets.save(email,"figma",project,id,row.label,row.remoteId,mapper.writeValueAsString(metadata),revision))
    }
    fun remove(email: String,project: UUID,id: UUID,revision: Long) = assets.remove(email,"figma",project,id,revision)
    private fun view(row: ProjectAsset) = FigmaFileView(row.id,row.label,row.remoteId,FigmaUrls.key(row.remoteId),mapper.readTree(row.payload).takeIf { it.has("name") }?.let { mapper.treeToValue(it,FigmaMetadata::class.java) },row.revision)
}
data class FigmaRefreshInput(val revision: Long)
@RestController @RequestMapping("/api/plugins/figma/projects/{projectId}/files")
class FigmaProjectController(private val service: FigmaProjectService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID) = service.list(user.username,projectId)
    @PostMapping fun link(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@RequestBody input: FigmaLinkInput) = service.link(user.username,projectId,input)
    @PostMapping("/{id}/refresh") fun refresh(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestBody input: FigmaRefreshInput) = service.refresh(user.username,projectId,id,input.revision)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestParam revision: Long) = service.remove(user.username,projectId,id,revision)
}
