package com.starter.plugins.documents

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.rendering.WeasyRendererClient
import com.starter.service.PdfService
import org.springframework.stereotype.Service

@Service
class GeneratedPdfContent(private val renderer: WeasyRendererClient, private val mapper: ObjectMapper, private val pdf: PdfService,
    private val storage: com.starter.service.pdfstorage.GeneratedPdfStorage? = null) {
    fun bytes(document: GeneratedDocument): ByteArray {
        if (document.pdfBytes.isNotEmpty()) return document.pdfBytes
        try { storage?.read(document.id, document.pdfBytes)?.let { return it } }
        catch (error: com.starter.service.pdfstorage.GeneratedPdfStorageUnavailable) { if (!document.pdfRegenerable) throw error }
        if (!document.pdfRegenerable) throw NotFoundException("Saved PDF unavailable")
        val template = mapper.readTree(document.templateSnapshot)
        val values: Map<String, String> = mapper.readValue(document.valuesJson, object : TypeReference<Map<String, String>>() {})
        val bytes = renderer.render(template, values)
        if (bytes.size > EmailAttachment.MAX_BYTES) throw BadRequestException("Regenerated PDF exceeds 10 MB")
        pdf.inspect(bytes, document.filename)
        // Serve the derivative without putting expired bytes back into database storage.
        return bytes
    }
}
