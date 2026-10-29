package com.starter.plugins.mermaid

import com.starter.plugins.*
import com.starter.plugins.projectassets.*
import com.starter.exception.BadRequestException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

@Component
class MermaidPlugin : WorkspacePlugin {
    override val manifest = PluginManifest("mermaid","Mermaid diagrams","1.0.0","Create and maintain project diagrams from Mermaid text with a local preview.",
        compatibleVersions=listOf("1.0.0","1.1.0"),extensions=setOf(PluginExtension.PAGE,PluginExtension.API,PluginExtension.DATABASE),
        categories=setOf("Development"),contextTargets=setOf("PROJECT"),hasAdminSettings=false,
        tables=listOf(PluginTable("plugin_mermaid_diagrams","Project diagram sources")))
}
data class MermaidInput(val title: String,val source: String,val revision: Long = -1)
data class MermaidView(val id: UUID,val title: String,val source: String,val revision: Long)
@Service
class MermaidProjectService(private val assets: ProjectAssetStore,private val mapper: ObjectMapper) {
    fun list(email: String,project: UUID) = assets.list(email,"mermaid",project).map(::view)
    fun save(email: String,project: UUID,id: UUID?,input: MermaidInput): MermaidView {
        if (input.source.isBlank() || input.source.length > 20000 || input.source.any { it.isISOControl() && it !in "\n\r\t" }) throw BadRequestException("Use 1–20000 characters of diagram source")
        return view(assets.save(email,"mermaid",project,id,input.title,"",mapper.writeValueAsString(mapOf("source" to input.source)),input.revision))
    }
    fun remove(email: String,project: UUID,id: UUID,revision: Long) = assets.remove(email,"mermaid",project,id,revision)
    private fun view(row: ProjectAsset) = MermaidView(row.id,row.label,mapper.readTree(row.payload).path("source").asText(),row.revision)
}
@RestController @RequestMapping("/api/plugins/mermaid/projects/{projectId}/diagrams")
class MermaidProjectController(private val service: MermaidProjectService) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID) = service.list(user.username,projectId)
    @PostMapping fun create(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@RequestBody input: MermaidInput) = service.save(user.username,projectId,null,input)
    @PutMapping("/{id}") fun save(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestBody input: MermaidInput) = service.save(user.username,projectId,id,input)
    @DeleteMapping("/{id}") fun remove(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID,@PathVariable id: UUID,@RequestParam revision: Long) = service.remove(user.username,projectId,id,revision)
}
