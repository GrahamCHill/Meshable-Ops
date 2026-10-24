package com.starter.projects

import com.starter.plugins.PluginRegistry
import com.starter.plugins.sourcecontrol.SourceControlService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ProjectBoardResource(val id: UUID,val boardId: UUID,val boardName: String,val cardId: UUID?,val kind: String,val title: String,val url: String,val state: String)
@Service
class ProjectBoardResources(private val jdbc: JdbcTemplate,private val access: ProjectAccess,private val boardAccess: ProjectBoardAccess,
    private val plugins: PluginRegistry,private val source: SourceControlService,private val settings: ProjectDevelopmentSettingsService) {
    fun list(email: String,projectId: UUID): List<ProjectBoardResource> {
        val actor=access.actor(email);access.require(projectId,actor)
        val features=settings.effective(projectId)
        if(!features.repositories || !access.hasFeature(actor,"plugin.kanban"))return emptyList()
        if(runCatching {plugins.requireTable("kanban","plugin_kanban_boards",actor.role);plugins.requireTable("kanban","plugin_kanban_development_links",actor.role)}.isFailure)return emptyList()
        val result=jdbc.query("""SELECT dl.*,b.name AS board_name FROM plugin_kanban_development_links dl JOIN plugin_kanban_boards b ON b.id=dl.board_id
            WHERE b.project_id=? AND ${ProjectAccessSql.boardPredicate()} AND (? OR dl.request_number IS NULL)
            AND (dl.card_id IS NULL OR EXISTS (SELECT 1 FROM jsonb_array_elements(CAST(b.content_json AS jsonb)->'cards') card WHERE card->>'id'=CAST(dl.card_id AS text)))
            ORDER BY dl.checked_at DESC,dl.id LIMIT 200""", {rs,_ ->
                val allowed=runCatching {source.approvedFor(rs.getString("provider_id"),rs.getString("repository"),actor.role)}.isSuccess
                if(!allowed)null else ProjectBoardResource(rs.getObject("id",UUID::class.java),rs.getObject("board_id",UUID::class.java),rs.getString("board_name"),rs.getObject("card_id",UUID::class.java),
                    if(rs.getObject("request_number")==null) "REPOSITORY" else "PULL_REQUEST",rs.getString("title"),rs.getString("url"),rs.getString("state"))
            },projectId,*boardAccess.sqlArgs(actor),features.pullRequests).filterNotNull()
        access.require(projectId,access.actor(email));return result
    }
}
@RestController @RequestMapping("/api/projects/{projectId}/development/board-links")
class ProjectBoardResourcesController(private val service: ProjectBoardResources) {
    @GetMapping fun list(@AuthenticationPrincipal user: UserDetails,@PathVariable projectId: UUID)=service.list(user.username,projectId)
}
