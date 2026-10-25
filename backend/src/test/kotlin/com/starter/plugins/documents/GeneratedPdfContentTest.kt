package com.starter.plugins.documents

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.NotFoundException
import com.starter.rendering.WeasyRendererClient
import com.starter.service.PdfService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.io.ByteArrayOutputStream

class GeneratedPdfContentTest {
    private val renderer = mock(WeasyRendererClient::class.java)
    private val mapper = jacksonObjectMapper()
    private val content = GeneratedPdfContent(renderer, mapper, PdfService())
    private val bytes = PDDocument().use { doc -> doc.addPage(PDPage()); ByteArrayOutputStream().use { doc.save(it); it.toByteArray() } }

    @Test fun `expired template bytes rebuild from the saved snapshot without caching them again`() {
        val template = mapper.readTree("""{"defaults":{"title":"Original title"}}""")
        val document = GeneratedDocument(templateSnapshot = template.toString(), valuesJson = """{"client":"Original client"}""", pdfRegenerable = true)
        `when`(renderer.render(template, mapOf("client" to "Original client"))).thenReturn(bytes)
        assertThat(content.bytes(document)).isEqualTo(bytes)
        assertThat(document.pdfBytes).isEmpty()
        assertThat(document.templateSnapshot).isEqualTo(template.toString())
        verify(renderer).render(template, mapOf("client" to "Original client"))
    }

    @Test fun `stored PDFs are returned without rendering and unclassified empty records never rebuild`() {
        assertThat(content.bytes(GeneratedDocument(pdfBytes = bytes))).isEqualTo(bytes)
        assertThatThrownBy { content.bytes(GeneratedDocument()) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(renderer)
        assertThat(GeneratedDocument().pdfRegenerable).isFalse()
    }
}
