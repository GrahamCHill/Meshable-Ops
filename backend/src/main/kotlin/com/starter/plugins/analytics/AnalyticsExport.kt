package com.starter.plugins.analytics

import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.projects.ProjectAccess
import com.starter.projects.ProjectService
import com.starter.rendering.WeasyRendererClient
import com.starter.security.RateLimits
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.branding.BrandingPreferencesService
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant
import java.util.UUID

data class AnalyticsExportDocument(val title: String,val organization: String,val contextName: String,val contextKind: String,
    val provider: String,val generatedAt: Instant,val links: List<AnalyticsLink>)
enum class AnalyticsExportFormat { CSV, PDF }

@Service
class AnalyticsExport(private val analytics: AnalyticsService,private val projects: ProjectAccess,private val clients: ProjectService,
    private val branding: BrandingPreferencesService,private val renderer: WeasyRendererClient,private val policy: DocumentAccessService,
    private val pdf: PdfService,private val rates: RateLimits) {
    fun download(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID,format: AnalyticsExportFormat,linkId: UUID?): ByteArray {
        val actor=projects.actor(email)
        rates.check("analytics-export-${p.id}",actor.id.toString(),30,3600)
        val access=policy.current()
        if(access.downloadMode==DownloadMode.DISABLED)throw ForbiddenException("Downloads are disabled by the current document policy")
        val links=selected(email,p,kind,context,linkId)
        if(format==AnalyticsExportFormat.PDF && links.size>30)throw BadRequestException("PDF exports support up to 30 snapshots. Export an individual link or use CSV for a larger set.")
        val name=if(kind==AnalyticsContext.PROJECT)projects.require(context,actor).name else clients.requireClient(email,context).name
        val document=AnalyticsExportDocument("${p.title} insights",branding.current().applicationName,name,kind.name,p.title,Instant.now(),links)
        val bytes=if(format==AnalyticsExportFormat.CSV)csv(document) else renderer.analytics(document).let {
            if(access.downloadMode==DownloadMode.WATERMARKED)pdf.watermark(it,access.watermarkText,email) else it
        }
        val current=selected(email,p,kind,context,linkId)
        if(current.map {it.id to it.revision}!=links.map {it.id to it.revision})throw ConflictException("Analytics snapshots changed during export. Retry to export the current values.")
        if(policy.current()!=access)throw ForbiddenException("Download policy changed. Refresh before exporting.")
        return bytes
    }
    private fun selected(email: String,p: AnalyticsProvider,kind: AnalyticsContext,context: UUID,linkId: UUID?): List<AnalyticsLink> {
        val links=analytics.list(email,p,kind,context)
        if(linkId!=null && links.none {it.id==linkId})throw NotFoundException("Analytics link unavailable")
        return links.filter {(linkId==null || it.id==linkId) && it.snapshot!=null}.also {
            if(it.isEmpty())throw BadRequestException("Refresh at least one linked resource before exporting insights")
        }
    }
    companion object {
        fun csv(document: AnalyticsExportDocument): ByteArray {
            val headers=listOf("Connector","Context type","Context","Link","Resource ID","Scope","Campaign / page selector","From","To","Snapshot refreshed (UTC)","Metric","Value","Availability","Unit","Currency","Reporting notes","Exported (UTC)")
            val rows=document.links.flatMap {link->val snapshot=link.snapshot ?: return@flatMap emptyList()
                snapshot.metrics.map {metric->listOf(document.provider,document.contextKind,document.contextName,link.label,link.selection.resourceId,link.selection.filter.name,link.selection.selector,
                    snapshot.from.toString(),snapshot.to.toString(),snapshot.syncedAt.toString(),metric.label,metric.value,if(metric.value==null)"UNAVAILABLE" else "AVAILABLE",metric.unit,snapshot.currency.orEmpty(),snapshot.notes.joinToString(" | "),document.generatedAt.toString())}
            }
            return ("\uFEFF"+(listOf(headers)+rows).joinToString("\r\n") {row->row.joinToString(",") {cell(it)}}+"\r\n").toByteArray(Charsets.UTF_8)
        }
        fun cell(value: Any?): String {
            val text=when(value) {null->"";is Number->value.toDouble().takeIf {it.isFinite()}?.let {value.toString()} ?: throw BadRequestException("Analytics contain an invalid number");else->value.toString()}
            val safe=if(value !is Number && (text.trimStart().firstOrNull() in listOf('=','+','-','@') || text.firstOrNull() in listOf('\t','\r','\n')))"'$text" else text
            return "\"${safe.replace("\"","\"\"")}\""
        }
    }
}

@RestController
@RequestMapping("/api/plugins/{pluginId:google-ads|meta-ads|google-analytics|meta-page-analytics|cloudflare-analytics}/contexts/{kind}/{contextId}/links/export")
class AnalyticsExportController(private val exports: AnalyticsExport) {
    @GetMapping("/{format}") fun download(@AuthenticationPrincipal user: UserDetails,@PathVariable pluginId: String,@PathVariable kind: AnalyticsContext,
        @PathVariable contextId: UUID,@PathVariable format: String,@RequestParam(required=false) linkId: UUID?): ResponseEntity<ByteArray> {
        val type=AnalyticsExportFormat.entries.find {it.name.equals(format,true)} ?: throw BadRequestException("Choose CSV or PDF export")
        val body=exports.download(user.username,AnalyticsProvider.from(pluginId),kind,contextId,type,linkId)
        return ResponseEntity.ok().contentType(if(type==AnalyticsExportFormat.PDF)MediaType.APPLICATION_PDF else MediaType("text","csv",Charsets.UTF_8))
            .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"$pluginId-${kind.name.lowercase()}-insights.${type.name.lowercase()}\"").body(body)
    }
}
