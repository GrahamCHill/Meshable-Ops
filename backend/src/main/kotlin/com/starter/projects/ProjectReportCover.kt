package com.starter.projects

import com.starter.exception.*
import com.starter.rendering.WeasyRendererClient
import com.starter.service.branding.BrandingPreferencesService
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.http.*
import java.util.UUID

@Entity @Table(name="workspace_project_report_designs")
class ProjectReportCoverRecord(@Id val projectId: UUID = UUID.randomUUID(), @Column(columnDefinition="TEXT") var html: String = "", @Version var revision: Long = 0)
interface ProjectReportCoverRepository: JpaRepository<ProjectReportCoverRecord,UUID>
data class ReportCoverInput(val html: String,val revision: Long = -1)
data class ReportCoverView(val html: String,val revision: Long,val canManage: Boolean)
data class ReportCoverPreviewInput(val html: String,val title: String = "Project work report",val period: String = "Reporting period")

@Service
class ProjectReportCoverService(private val rows: ProjectReportCoverRepository,private val access: ProjectAccess,private val renderer: WeasyRendererClient,
    private val branding: BrandingPreferencesService,private val jdbc: JdbcTemplate) {
    fun currentHtml(projectId: UUID)=rows.findById(projectId).orElse(null)?.html?.takeIf {it.isNotBlank()} ?: DEFAULT_HTML
    fun view(email: String,id: UUID): ReportCoverView {
        val actor=access.actor(email);val project=access.require(id,actor);val row=rows.findById(id).orElse(null)
        return ReportCoverView(currentHtml(id),row?.revision ?: -1,access.role(actor,project)!=ProjectRole.MEMBER)
    }
    @Transactional fun save(email: String,id: UUID,input: ReportCoverInput): ReportCoverView {
        access.require(id,access.actor(email),true);validate(input.html)
        renderer.validateReportCover(input.html)
        access.require(id,access.actor(email),true)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"project-report-cover:$id")
        val old=rows.findById(id).orElse(null)
        if((old?.revision ?: -1)!=input.revision)throw ConflictException("The cover design changed. Reload before saving.")
        val row=old ?: ProjectReportCoverRecord(id);row.html=input.html;rows.saveAndFlush(row)
        return view(email,id)
    }
    fun preview(email: String,id: UUID,input: ReportCoverPreviewInput): ByteArray {
        val actor=access.actor(email);val project=access.require(id,actor,true);validate(input.html)
        if(input.title.isBlank() || input.title.length>200 || input.period.length>100)throw BadRequestException("Use a short report title and period")
        val result=renderer.reportCover(mapOf("html" to input.html,"title" to input.title,"organization" to branding.current().applicationName,
            "project" to project.name,"period" to input.period,"ticketCount" to "Preview"))
        access.require(id,access.actor(email),true);return result
    }
    private fun validate(html: String) {if(html.isBlank() || html.length>30000)throw BadRequestException("Enter an HTML cover design of up to 30,000 characters")}
    companion object {
        val DEFAULT_HTML="""<!doctype html><html><head><meta charset="utf-8"><style>
            @page {size:A4;margin:0} body {margin:0;font-family:DejaVu Sans,sans-serif;color:#132842}
            .cover {height:297mm;box-sizing:border-box;padding:28mm 22mm;background:#f4f7fb;border-top:12mm solid #2367c9}
            .brand {font-size:12pt;letter-spacing:2px;color:#2367c9;margin-bottom:40mm}
            h1 {font-size:36pt;line-height:1.16;margin:0 0 12mm;overflow-wrap:anywhere}
            .project {font-size:18pt;line-height:1.4;overflow-wrap:anywhere}.period {font-size:13pt;color:#526579}
            .rule {height:2mm;width:24mm;background:#2367c9;margin:20mm 0 12mm}.foot {font-size:9pt;color:#526579}
            </style></head><body><main class="cover"><p class="brand">{{organization}}</p><h1>{{title}}</h1>
            <p class="project">{{project}}</p><p class="period">{{period}}</p><div class="rule"></div>
            <p class="foot">{{ticketCount}} tickets in scope<br>Generated {{generatedAt}}<br>Internal project report</p></main></body></html>""".trimIndent()
    }
}
@RestController @RequestMapping("/api/projects/{id}/reports/design")
class ProjectReportCoverController(private val service: ProjectReportCoverService,private val rates: com.starter.security.RateLimits) {
    @GetMapping fun view(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID)=service.view(user.username,id)
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@RequestBody input: ReportCoverInput): ReportCoverView {rates.check("report-cover",user.username,10,60);return service.save(user.username,id,input)}
    @PostMapping("/preview") fun preview(@AuthenticationPrincipal user: UserDetails,@PathVariable id: UUID,@RequestBody input: ReportCoverPreviewInput): ResponseEntity<ByteArray> {
        rates.check("report-cover",user.username,10,60)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=report-cover-preview.pdf").body(service.preview(user.username,id,input))
    }
}
