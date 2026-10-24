package com.starter.projects

import com.starter.model.postgres.User
import com.starter.plugins.PluginRegistry
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

data class WorkspaceContext(val kind: String, val id: UUID, val name: String)
data class ContextPanel(val title: String, val text: String)
data class ContextExtension(val pluginId: String, val label: String, val href: String, val panels: List<ContextPanel> = emptyList())
/** Reviewed modules can add computed panels and scoped APIs; the host always authorizes the context first. */
interface WorkspaceContextProvider {
    val pluginId: String
    fun panels(context: WorkspaceContext, actor: User): List<ContextPanel>
}

@Service
class ProjectExtensions(private val projects: ProjectAccess, private val service: ProjectService, private val plugins: PluginRegistry,
    private val providers: List<WorkspaceContextProvider>) {
    fun list(email: String, kind: String, id: UUID): List<ContextExtension> {
        val actor = projects.actor(email)
        val name = if (kind == "PROJECT") projects.require(id, actor).name else service.getClient(email, id).name
        val context = WorkspaceContext(kind, id, name)
        return plugins.available(actor.role).filter { kind in it.manifest.contextTargets }.map { plugin ->
            val panels = providers.filter { it.pluginId == plugin.manifest.id }.flatMap { it.panels(context, actor) }.take(20)
            ContextExtension(plugin.manifest.id, plugin.manifest.name, "/plugins/${plugin.manifest.id}?${kind.lowercase()}=$id", panels)
        }
    }
}

@RestController @RequestMapping("/api/projects")
class ProjectExtensionsController(private val extensions: ProjectExtensions) {
    @GetMapping("/{id}/extensions") fun projects(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: UUID) = extensions.list(actor.username, "PROJECT", id)
    @GetMapping("/clients/{id}/extensions") fun clients(@AuthenticationPrincipal actor: UserDetails, @PathVariable id: UUID) = extensions.list(actor.username, "CLIENT", id)
}
