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
class ProjectReportCoverRecord(@Id val projectId: UUID = UUID.randomUUID(), @Column(columnDefinition="TEXT") var html: String = "", @Version var revision: Long = 0,
    @Column(columnDefinition="TEXT",nullable=false) @org.hibernate.annotations.ColumnDefault("'{}'") var assetsJson: String = "{}")
interface ProjectReportCoverRepository: JpaRepository<ProjectReportCoverRecord,UUID>
data class ReportCoverInput(val html: String,val revision: Long = -1,val assets: Map<String,String>? = null)
data class ReportCoverView(val html: String,val revision: Long,val canManage: Boolean,val defaultHtml: String = ProjectReportCoverService.DEFAULT_HTML,val assets: Map<String,String> = emptyMap())
data class ReportCoverPreviewInput(val html: String,val title: String = "Project work report",val period: String = "Reporting period",val assets: Map<String,String>? = null)
data class ReportCoverContext(val html: String,val assets: Map<String,String>,val siteName: String,val companyName: String,val siteLogo: String)

@Service
class ProjectReportCoverService(private val rows: ProjectReportCoverRepository,private val access: ProjectAccess,private val renderer: WeasyRendererClient,
    private val branding: BrandingPreferencesService,private val jdbc: JdbcTemplate,
    private val brandAssets: com.starter.repository.postgres.BrandingAssetRepository? = null) {
    private val mapper=com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    private fun assets(row: ProjectReportCoverRecord?)=row?.let {mapper.readValue(it.assetsJson,object:com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {})} ?: emptyMap()
    fun context(projectId: UUID): ReportCoverContext {
        val settings=branding.current()
        val row=rows.findById(projectId).orElse(null)
        val logo=brandAssets?.findById(com.starter.model.postgres.BrandingKind.LOGO)?.orElse(null)?.png?.let {ReportCoverImages.encode(it)}.orEmpty()
        return ReportCoverContext(row?.html?.takeIf {it.isNotBlank()} ?: DEFAULT_HTML,assets(row),settings.applicationName,settings.companyName,logo)
    }
    fun currentHtml(projectId: UUID)=rows.findById(projectId).orElse(null)?.html?.takeIf {it.isNotBlank()} ?: DEFAULT_HTML
    fun view(email: String,id: UUID): ReportCoverView {
        val actor=access.actor(email);val project=access.require(id,actor);val row=rows.findById(id).orElse(null)
        return ReportCoverView(row?.html?.takeIf {it.isNotBlank()} ?: DEFAULT_HTML,row?.revision ?: -1,access.role(actor,project)!=ProjectRole.MEMBER,assets=assets(row))
    }
    @Transactional fun save(email: String,id: UUID,input: ReportCoverInput): ReportCoverView {
        access.require(id,access.actor(email),true);validate(input.html)
        val savedImages=assets(rows.findById(id).orElse(null))
        val images=input.assets?.let {ReportCoverImages.normalize(it,savedImages)} ?: savedImages
        render(email,id,ReportCoverPreviewInput(input.html),images) // Validate real layout in the bounded worker before saving.
        access.require(id,access.actor(email),true)
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",RowCallbackHandler {},"project-report-cover:$id")
        val old=rows.findById(id).orElse(null)
        if((old?.revision ?: -1)!=input.revision)throw ConflictException("The cover design changed. Reload before saving.")
        val row=old ?: ProjectReportCoverRecord(id);row.html=input.html;row.assetsJson=mapper.writeValueAsString(images);rows.saveAndFlush(row)
        return view(email,id)
    }
    fun preview(email: String,id: UUID,input: ReportCoverPreviewInput): ByteArray {
        access.require(id,access.actor(email),true)
        val savedImages=assets(rows.findById(id).orElse(null))
        return render(email,id,input,input.assets?.let {ReportCoverImages.normalize(it,savedImages)} ?: savedImages)
    }
    private fun render(email: String,id: UUID,input: ReportCoverPreviewInput,images: Map<String,String>): ByteArray {
        val actor=access.actor(email);val project=access.require(id,actor,true);validate(input.html)
        if(input.title.isBlank() || input.title.length>200 || input.period.length>100)throw BadRequestException("Use a short report title and period")
        val context=context(id)
        val result=renderer.reportCover(mapOf("html" to input.html,"title" to input.title,"organization" to context.siteName,
            "project" to project.name,"period" to input.period,"ticketCount" to "Preview","assets" to images,"siteName" to context.siteName,"companyName" to context.companyName,"siteLogo" to context.siteLogo))
        access.require(id,access.actor(email),true);return result
    }
    private fun validate(html: String) {if(html.isBlank() || html.length>30000)throw BadRequestException("Enter an HTML cover design of up to 30,000 characters")}
    companion object {
        val DEFAULT_HTML="""<!doctype html><html><head><meta charset="utf-8"><style>
            @page {size:A4;margin:0} body {margin:0;font-family:Liberation Sans,sans-serif;color:#132842}
            .cover {height:297mm;box-sizing:border-box;padding:28mm 22mm;background:#f4f7fb;border-top:12mm solid #2367c9}
            .brand {font-size:12pt;letter-spacing:2px;color:#2367c9;margin-bottom:25mm}.logo {max-width:50mm;max-height:16mm}.company {font-size:10pt;letter-spacing:0}
            h1 {font-size:36pt;line-height:1.16;margin:0 0 12mm;overflow-wrap:anywhere}
            .project {font-size:18pt;line-height:1.4;overflow-wrap:anywhere}.period {font-size:13pt;color:#526579}
            .rule {height:2mm;width:24mm;background:#2367c9;margin:20mm 0 12mm}.foot {font-size:9pt;color:#526579}
            </style></head><body><main class="cover"><div class="brand"><img class="logo" src="{{siteLogo}}" alt="Site logo"><p>{{siteName}}</p><p class="company">{{companyName}}</p></div><h1>{{title}}</h1>
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
