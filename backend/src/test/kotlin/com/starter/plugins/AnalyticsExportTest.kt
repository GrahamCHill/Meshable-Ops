package com.starter.plugins

import com.starter.dto.BrandingPreferences
import com.starter.dto.DocumentAccessPolicy
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.model.postgres.User
import com.starter.plugins.analytics.*
import com.starter.projects.*
import com.starter.rendering.WeasyRendererClient
import com.starter.security.RateLimits
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.branding.BrandingPreferencesService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AnalyticsExportTest {
    private val actor=User(email="reader@example.test");private val context=UUID.randomUUID();private val id=UUID.randomUUID()
    private val p=AnalyticsProvider.META_ADS
    private val analytics=mock(AnalyticsService::class.java);private val projects=mock(ProjectAccess::class.java);private val clients=mock(ProjectService::class.java)
    private val branding=mock(BrandingPreferencesService::class.java);private val policy=mock(DocumentAccessService::class.java);private val pdf=mock(PdfService::class.java)
    private var rendered:AnalyticsExportDocument?=null
    private val renderer=mock(WeasyRendererClient::class.java) {call->if(call.method.name=="analytics") {rendered=call.arguments[0] as AnalyticsExportDocument;"%PDF-test-export".toByteArray()} else RETURNS_DEFAULTS.answer(call)}
    private val exports=AnalyticsExport(analytics,projects,clients,branding,renderer,policy,pdf,mock(RateLimits::class.java))
    private val snapshot=AnalyticsSnapshot(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-24"),listOf(AnalyticsMetric("clicks","Clicks",42.0),AnalyticsMetric("revenue","Revenue",-12.5,"CURRENCY"),AnalyticsMetric("reach","Reach",null)),"ZAR",listOf("Provider attribution applies"),Instant.parse("2026-10-25T01:00:00Z"))
    private val link=AnalyticsLink(id,"Launch",AnalyticsSelection("123",AnalyticsFilter.CAMPAIGN,"456"),snapshot,2,"https://example.test")
    init {
        `when`(projects.actor(actor.email)).thenReturn(actor)
        `when`(projects.require(context,actor,false)).thenReturn(ProjectRecord(id=context,name="Client launch"))
        `when`(analytics.list(actor.email,p,AnalyticsContext.PROJECT,context)).thenReturn(listOf(link))
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
        `when`(branding.current()).thenReturn(BrandingPreferences())
    }
    @Test fun `CSV exports saved scoped values notes and availability without a PDF renderer`() {
        val text=exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.CSV,null).toString(Charsets.UTF_8)
        assertThat(text).startsWith("\uFEFF").contains("Meta Ads","Client launch","CAMPAIGN","456","2026-10-01","2026-10-24","Provider attribution applies","UNAVAILABLE","ZAR")
        assertThat(text).contains("\"-12.5\"").doesNotContain("\"'-12.5\"")
        verifyNoInteractions(renderer,pdf)
        verify(analytics,times(2)).list(actor.email,p,AnalyticsContext.PROJECT,context)
    }
    @Test fun `CSV neutralizes spreadsheet formulas and preserves quoting and newlines`() {
        assertThat(AnalyticsExport.cell(" =HYPERLINK(\"https://example.test\")")).startsWith("\"' =HYPERLINK(")
        assertThat(AnalyticsExport.cell("@SUM(1,2)")).isEqualTo("\"'@SUM(1,2)\"")
        assertThat(AnalyticsExport.cell("Line 1\nA \"quote\", here")).isEqualTo("\"Line 1\nA \"\"quote\"\", here\"")
        assertThat(AnalyticsExport.cell(null)).isEqualTo("\"\"")
    }
    @Test fun `PDF has the selected resource and current policy watermark`() {
        val access=DocumentAccessPolicy(downloadMode=DownloadMode.WATERMARKED,watermarkText="Internal")
        `when`(policy.current()).thenReturn(access)
        val original="%PDF-test-export".toByteArray();val marked="%PDF-watermarked".toByteArray()
        `when`(pdf.watermark(original,"Internal",actor.email)).thenReturn(marked)
        assertThat(exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.PDF,id)).isEqualTo(marked)
        assertThat(rendered!!.provider).isEqualTo("Meta Ads");assertThat(rendered!!.links).containsExactly(link)
    }
    @Test fun `revocation and concurrent snapshot changes block delivery after rendering`() {
        `when`(analytics.list(actor.email,p,AnalyticsContext.PROJECT,context)).thenReturn(listOf(link),listOf(link.copy(revision=3)))
        assertThatThrownBy {exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.PDF,id)}.isInstanceOf(ConflictException::class.java)
        `when`(analytics.list(actor.email,p,AnalyticsContext.PROJECT,context)).thenReturn(listOf(link),emptyList())
        assertThatThrownBy {exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.PDF,id)}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `disabled downloads and unavailable links fail before rendering`() {
        `when`(policy.current()).thenReturn(DocumentAccessPolicy(downloadMode=DownloadMode.DISABLED))
        assertThatThrownBy {exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.PDF,id)}.isInstanceOf(ForbiddenException::class.java)
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
        assertThatThrownBy {exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.PDF,UUID.randomUUID())}.isInstanceOf(NotFoundException::class.java)
        `when`(analytics.list(actor.email,p,AnalyticsContext.PROJECT,context)).thenReturn(listOf(link.copy(snapshot=null)))
        assertThatThrownBy {exports.download(actor.email,p,AnalyticsContext.PROJECT,context,AnalyticsExportFormat.CSV,null)}.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(renderer,pdf)
    }
}
