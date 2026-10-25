package com.starter.projects

import com.starter.dto.BrandingPreferences
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.rendering.WeasyRendererClient
import com.starter.service.branding.BrandingPreferencesService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProjectReportCoverTest {
    private val rows=mock(ProjectReportCoverRepository::class.java);private val access=mock(ProjectAccess::class.java)
    private val renderer=mock(WeasyRendererClient::class.java);private val branding=mock(BrandingPreferencesService::class.java)
    private val brandAssets=mock(com.starter.repository.postgres.BrandingAssetRepository::class.java)
    private val artwork=mock(com.starter.repository.postgres.WorkspaceArtworkRepository::class.java)
    private val actor=User(email="admin@example.test",role="ADMIN");private val project=ProjectRecord(name="Delivery",clientId=UUID.randomUUID())
    private val service=ProjectReportCoverService(rows,access,renderer,branding,mock(JdbcTemplate::class.java),brandAssets,artwork)
    init {
        `when`(access.actor(actor.email)).thenReturn(actor);`when`(access.require(project.id,actor,true)).thenReturn(project)
        `when`(access.require(project.id,actor,false)).thenReturn(project);`when`(access.role(actor,project)).thenReturn(ProjectRole.MANAGER)
        `when`(branding.current()).thenReturn(BrandingPreferences())
        `when`(brandAssets.findById(com.starter.model.postgres.BrandingKind.LOGO)).thenReturn(Optional.empty())
        `when`(renderer.reportCover(anyMap<String,Any>())).thenReturn("%PDF-preview".toByteArray())
        `when`(rows.findById(1)).thenReturn(Optional.empty())
        `when`(rows.saveAndFlush(any(ProjectReportCoverRecord::class.java))).thenAnswer {call->val row=call.arguments[0] as ProjectReportCoverRecord;`when`(rows.findById(1)).thenReturn(Optional.of(row));row}
    }
    @Test fun `stale revisions cannot overwrite the shared design`() {
        assertThatThrownBy {service.save(actor.email,ReportCoverInput("<h1>Design</h1>",7))}.isInstanceOf(ConflictException::class.java)
        verify(rows,never()).saveAndFlush(any(ProjectReportCoverRecord::class.java))
    }
    @Test fun `saved cover layouts are rendered first and unrelated settings stay outside the document`() {
        val html="<h1>{{project}}</h1><p>{{period}}</p>"
        val saved=service.save(actor.email,ReportCoverInput(html,-1))
        assertThat(saved.html).isEqualTo(html);assertThat(saved.revision).isEqualTo(0);assertThat(saved.canManage).isTrue()
        verify(renderer).reportCover(mapOf("html" to html,"title" to "Project work report","organization" to BrandingPreferences().applicationName,"project" to "Example project","projectName" to "Example project","period" to "Reporting period","ticketCount" to "Preview","assets" to emptyMap<String,String>(),"siteName" to BrandingPreferences().applicationName,"companyName" to "","siteLogo" to "","clientLogo" to ""))
        assertThatThrownBy {service.save(actor.email,ReportCoverInput("x".repeat(30001),0))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `cover images are saved atomically with HTML and older requests retain their images`() {
        val image=java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val bytes=java.io.ByteArrayOutputStream().also {javax.imageio.ImageIO.write(image,"png",it)}.toByteArray()
        val source="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(bytes)
        val html="<h1>{{siteName}}</h1><img src=\"images/Banner.png\">"
        val saved=service.save(actor.email,ReportCoverInput(html,-1,mapOf("Banner.png" to source)))
        assertThat(saved.assets.keys).containsExactly("banner.png")
        val updated=service.save(actor.email,ReportCoverInput(html,0))
        assertThat(updated.assets).isEqualTo(saved.assets)
        assertThat(service.context(project).assets).isEqualTo(saved.assets)
        assertThat(service.save(actor.email,ReportCoverInput("<h1>Without image</h1>",0,emptyMap())).assets).isEmpty()
    }
    @Test fun `report contexts use the configured site identity and an embedded logo`() {
        val image=java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val png=java.io.ByteArrayOutputStream().also {javax.imageio.ImageIO.write(image,"png",it)}.toByteArray()
        `when`(brandAssets.findById(com.starter.model.postgres.BrandingKind.LOGO)).thenReturn(Optional.of(com.starter.model.postgres.BrandingAsset(png=png)))
        `when`(branding.current()).thenReturn(BrandingPreferences(applicationName="Delivery site",companyName="Example Company"))
        val value=service.context(project)
        assertThat(value.siteName).isEqualTo("Delivery site");assertThat(value.companyName).isEqualTo("Example Company")
        assertThat(value.siteLogo).startsWith("data:image/png;base64,")
    }
    @ParameterizedTest @ValueSource(strings=["USER","BUSINESS_MANAGER","EXTERNAL"])
    fun `project managers and members cannot access the shared report designer`(role: String) {
        actor.role=role
        assertThatThrownBy {service.view(actor.email)}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {service.save(actor.email,ReportCoverInput("<h1>Cover</h1>"))}.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy {service.preview(actor.email,ReportCoverPreviewInput("<h1>Cover</h1>"))}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(rows,renderer)
    }
    @Test fun `one shared design uses each projects own client logo`() {
        val image=java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val png=java.io.ByteArrayOutputStream().also {javax.imageio.ImageIO.write(image,"png",it)}.toByteArray()
        `when`(artwork.findById("client:${project.clientId}")).thenReturn(Optional.of(com.starter.model.postgres.WorkspaceArtwork(image=png)))
        val saved=service.save(actor.email,ReportCoverInput("<h1>{{projectName}}</h1><img src=\"{{clientLogo}}\">",aiInsightsEnabled=true))
        assertThat(service.context(project).clientLogo).startsWith("data:image/png;base64,")
        val other=service.context(ProjectRecord(name="Another project"))
        assertThat(other.html).isEqualTo(saved.html);assertThat(other.clientLogo).isEmpty()
        assertThat(service.aiInsightsEnabled()).isTrue()
        assertThat(saved.aiInsightsEnabled).isTrue()
    }
    @Test fun `preview resolves the selected project without creating a project design`() {
        service.preview(actor.email,ReportCoverPreviewInput("<h1>{{projectName}}</h1>",projectId=project.id))
        val rendered=mockingDetails(renderer).invocations.single {it.method.name=="reportCover"}.arguments[0] as Map<*,*>
        assertThat(rendered["projectName"]).isEqualTo("Delivery")
        verify(rows,never()).saveAndFlush(any(ProjectReportCoverRecord::class.java))
    }
}
