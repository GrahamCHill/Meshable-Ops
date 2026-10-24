package com.starter.projects

import com.starter.exception.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

data class ProjectDirectoryPage(val items: List<ProjectSummary>,val hasMore: Boolean,val nextOffset: Int)
data class ProjectSectionMove(val sectionId: String,val revision: Long)
@Service
class ProjectDirectory(private val projects: ProjectRepository,private val access: ProjectAccess,private val settings: ProjectSettingsService,private val service: ProjectService,private val jdbc: JdbcTemplate) {
    fun page(email: String,query: String,category: String,clientId: UUID?,tags: Set<UUID>,offset: Int,limit: Int): ProjectDirectoryPage {
        if(limit !in 1..10)throw BadRequestException("Load between 1 and 10 projects at a time")
        val rows=service.list(email,query,category,tags,clientId,offset,limit+1)
        return ProjectDirectoryPage(rows.take(limit),rows.size>limit,offset+minOf(limit,rows.size))
    }
    @Transactional fun move(email: String,id: UUID,input: ProjectSectionMove) {
        val actor=access.actor(email);val row=access.require(id,actor)
        jdbc.execute("SELECT pg_advisory_xact_lock_shared(50272026)")
        val configuration=settings.current().settings
        if(!ProjectSectionRules.canMove(actor,configuration))throw ForbiddenException("You are not allowed to move projects between sections")
        if(row.revision!=input.revision)throw ConflictException("This project changed. Reload before moving it.")
        row.category=ProjectSectionRules.require(configuration,input.sectionId);row.updatedAt=Instant.now();projects.saveAndFlush(row)
    }
}
@RestController @RequestMapping("/api/projects")
class ProjectDirectoryController(private val service: ProjectDirectory) {
    @GetMapping("/page") fun page(@AuthenticationPrincipal user: UserDetails,@RequestParam(defaultValue="") query: String,@RequestParam category: String,
        @RequestParam(required=false) clientId: UUID?,@RequestParam(required=false) tags: Set<UUID>?,@RequestParam(defaultValue="0") offset: Int,@RequestParam(defaultValue="10") limit: Int)=service.page(user.username,query,category,clientId,tags ?: emptySet(),offset,limit)
    @PostMapping("/{id}/section") fun move(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@RequestBody input: ProjectSectionMove)=service.move(user.username,id,input)
}
