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

class ProjectReportCoverTest {
    private val rows=mock(ProjectReportCoverRepository::class.java);private val access=mock(ProjectAccess::class.java)
    private val renderer=mock(WeasyRendererClient::class.java);private val branding=mock(BrandingPreferencesService::class.java)
    private val actor=User(email="manager@example.test");private val project=ProjectRecord(name="Delivery")
    private val service=ProjectReportCoverService(rows,access,renderer,branding,mock(JdbcTemplate::class.java))
    init {
        `when`(access.actor(actor.email)).thenReturn(actor);`when`(access.require(project.id,actor,true)).thenReturn(project)
        `when`(access.require(project.id,actor,false)).thenReturn(project);`when`(access.role(actor,project)).thenReturn(ProjectRole.MANAGER)
        `when`(branding.current()).thenReturn(BrandingPreferences())
        `when`(renderer.reportCover(anyMap<String,Any>())).thenReturn("%PDF-preview".toByteArray())
        `when`(rows.findById(project.id)).thenReturn(Optional.empty())
        `when`(rows.saveAndFlush(any(ProjectReportCoverRecord::class.java))).thenAnswer {call->val row=call.arguments[0] as ProjectReportCoverRecord;`when`(rows.findById(project.id)).thenReturn(Optional.of(row));row}
    }
    @Test fun `cover edits require project management and stale revisions cannot overwrite a design`() {
        doThrow(ForbiddenException("Read only")).`when`(access).require(project.id,actor,true)
        assertThatThrownBy {service.save(actor.email,project.id,ReportCoverInput("<h1>Design</h1>"))}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(renderer,rows)
        doReturn(project).`when`(access).require(project.id,actor,true)
        assertThatThrownBy {service.save(actor.email,project.id,ReportCoverInput("<h1>Design</h1>",7))}.isInstanceOf(ConflictException::class.java)
        verify(rows,never()).saveAndFlush(any(ProjectReportCoverRecord::class.java))
    }
    @Test fun `saved cover layouts are rendered first and unrelated settings stay outside the document`() {
        val html="<h1>{{project}}</h1><p>{{period}}</p>"
        val saved=service.save(actor.email,project.id,ReportCoverInput(html,-1))
        assertThat(saved.html).isEqualTo(html);assertThat(saved.revision).isEqualTo(0);assertThat(saved.canManage).isTrue()
        verify(renderer).reportCover(mapOf("html" to html,"title" to "Project work report","organization" to BrandingPreferences().applicationName,"project" to "Delivery","period" to "Reporting period","ticketCount" to "Preview","assets" to emptyMap<String,String>(),"siteName" to BrandingPreferences().applicationName,"companyName" to "","siteLogo" to ""))
        assertThatThrownBy {service.save(actor.email,project.id,ReportCoverInput("x".repeat(30001),0))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `cover images are saved atomically with HTML and older requests retain their images`() {
        val image=java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val bytes=java.io.ByteArrayOutputStream().also {javax.imageio.ImageIO.write(image,"png",it)}.toByteArray()
        val source="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(bytes)
        val html="<h1>{{siteName}}</h1><img src=\"images/Banner.png\">"
        val saved=service.save(actor.email,project.id,ReportCoverInput(html,-1,mapOf("Banner.png" to source)))
        assertThat(saved.assets.keys).containsExactly("banner.png")
        val updated=service.save(actor.email,project.id,ReportCoverInput(html,0))
        assertThat(updated.assets).isEqualTo(saved.assets)
        assertThat(service.context(project.id).assets).isEqualTo(saved.assets)
        assertThat(service.save(actor.email,project.id,ReportCoverInput("<h1>Without image</h1>",0,emptyMap())).assets).isEmpty()
    }
}
