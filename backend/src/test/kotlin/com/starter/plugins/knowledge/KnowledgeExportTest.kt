package com.starter.plugins.knowledge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.DocumentAccessPolicy
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.rendering.WeasyRendererClient
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PageMode
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.io.ByteArrayOutputStream

class KnowledgeExportTest {
    private val access = mock(KnowledgeAccess::class.java)
    private val pages = mock(KnowledgeService::class.java)
    private val covers = mock(KnowledgeSettingsService::class.java)
    private val renderer = mock(WeasyRendererClient::class.java)
    private val downloads = mock(DocumentAccessService::class.java)
    private val pdfs = mock(PdfService::class.java)
    private val first = KnowledgePage(title = "First", markdown = "## Overview\n\nFirst document")
    private val second = KnowledgePage(title = "Second", markdown = "Second document")
    private val settings = KnowledgeSettings(revision = 3)
    private val email = "exporter@example.test"
    private val input = KnowledgeExportInput("Team handbook", listOf(KnowledgeSelection(second.id, 0), KnowledgeSelection(first.id, 0)))
    private val service = KnowledgeExportService(access, pages, KnowledgeMarkdown(), covers, renderer, jacksonObjectMapper(), downloads, pdfs)
    init {
        `when`(access.requireExport(email)).thenReturn(settings)
        `when`(downloads.current()).thenReturn(DocumentAccessPolicy())
        `when`(pages.find(first.id)).thenReturn(first); `when`(pages.find(second.id)).thenReturn(second)
        `when`(pages.assets(first)).thenReturn(emptyMap()); `when`(pages.assets(second)).thenReturn(emptyMap())
        `when`(covers.cover(settings, input.title, 2)).thenReturn(emptyMap())
    }
    @Test fun `export requires a grant before preparation or rendering`() {
        `when`(access.requireExport(email)).thenThrow(ForbiddenException("Not selected"))
        assertThatThrownBy { service.prepare(email, input) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { service.export(email, input) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(pages, renderer, covers)
    }
    @Test fun `selected documents retain their order and every physical page gets a number and bookmark`() {
        doAnswer { call ->
            val payload = call.arguments[0] as Map<*, *>
            val ordered = payload["pages"] as List<*>
            assertThat(ordered.map { (it as Map<*, *>)["title"] }).containsExactly("Second", "First")
            pdf()
        }.`when`(renderer).knowledge(anyMap<String, Any>())
        val bytes = service.export(email, input)
        Loader.loadPDF(bytes).use { document ->
            assertThat(document.documentCatalog.pageMode).isEqualTo(PageMode.USE_OUTLINES)
            assertThat(PDFTextStripper().getText(document)).contains("Page 1 of 2", "Page 2 of 2")
            assertThat(document.documentCatalog.documentOutline.firstChild.children().map { it.title }).containsExactly("Page 1", "Page 2")
        }
    }
    @Test fun `revocation during rendering suppresses the completed PDF`() {
        `when`(renderer.knowledge(anyMap<String, Any>())).thenReturn(pdf())
        `when`(access.requireExport(email)).thenReturn(settings).thenThrow(ForbiddenException("Revoked"))
        assertThatThrownBy { service.export(email, input) }.isInstanceOf(ForbiddenException::class.java).hasMessageContaining("Revoked")
        verify(renderer).knowledge(anyMap<String, Any>())
    }
    @Test fun `watermarked document policy applies to knowledge books`() {
        `when`(downloads.current()).thenReturn(DocumentAccessPolicy(downloadMode = DownloadMode.WATERMARKED, watermarkText = "Private"))
        `when`(renderer.knowledge(anyMap<String, Any>())).thenReturn(pdf())
        val marked = byteArrayOf(1, 2, 3)
        doAnswer { call ->
            assertThat(call.arguments[1]).isEqualTo("Private")
            assertThat(call.arguments[2]).isEqualTo(email)
            marked
        }.`when`(pdfs).watermark(any(ByteArray::class.java) ?: byteArrayOf(), anyString(), anyString())
        assertThat(service.export(email, input)).isEqualTo(marked)
        verify(pdfs).watermark(any(ByteArray::class.java) ?: byteArrayOf(), anyString(), anyString())
    }
    @Test fun `changed or deleted pages and stale settings cannot release a PDF`() {
        `when`(renderer.knowledge(anyMap<String, Any>())).thenReturn(pdf())
        `when`(pages.find(first.id)).thenReturn(first).thenReturn(KnowledgePage(id = first.id, revision = 1))
        assertThatThrownBy { service.export(email, input) }.isInstanceOf(ConflictException::class.java)
        `when`(pages.find(first.id)).thenReturn(first)
        `when`(access.requireExport(email)).thenReturn(settings).thenReturn(KnowledgeSettings(revision = 4))
        assertThatThrownBy { service.export(email, input) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `global download restrictions duplicate selections and omitted diagrams cannot bypass export checks`() {
        `when`(downloads.current()).thenReturn(DocumentAccessPolicy(downloadMode = DownloadMode.DISABLED))
        assertThatThrownBy { service.export(email, input) }.isInstanceOf(ForbiddenException::class.java)
        `when`(downloads.current()).thenReturn(DocumentAccessPolicy())
        assertThatThrownBy { service.prepare(email, input.copy(pages = listOf(input.pages.first(), input.pages.first()))) }.isInstanceOf(BadRequestException::class.java)
        first.markdown = "```mermaid\nflowchart LR\n A --> B\n```"
        assertThatThrownBy { service.export(email, input) }.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(renderer)
    }
    private fun pdf() = PDDocument().use { document -> document.addPage(PDPage()); document.addPage(PDPage()); ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() } }
}
