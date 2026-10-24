package com.starter.plugins.documents

import com.starter.rendering.WeasyRendererClient

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.security.RateLimits
import com.starter.service.PdfService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GeneratedDocumentService(private val documents: GeneratedDocumentRepository, private val templates: DocumentTemplateService,
    private val access: DocumentAccess, private val fields: DocumentFields, private val renderer: WeasyRendererClient,
    private val mapper: ObjectMapper, private val pdf: PdfService, private val delivery: GeneratedDocumentDelivery, private val rates: RateLimits) {
    fun list(email: String): List<GeneratedDocumentView> { val actor = access.actor(email); access.library(actor); return documents.visible(actor.id, com.starter.access.Roles.admin(actor.role), PageRequest.of(0, 100)) }
    fun generate(email: String, request: GenerateDocumentRequest): GeneratedDocumentView {
        val actor = access.actor(email); access.library(actor, write = true); rates.check("generate-document", actor.id.toString(), 20, 3600)
        val template = templates.get(email, request.templateId); val source = mapper.readTree(template.templateJson)
        val values = fields.resolve(source, templates.rules(template), request.values)
        val bytes = renderer.render(source, values)
        if (bytes.size > EmailAttachment.MAX_BYTES) throw BadRequestException("Generated PDF exceeds 10 MB")
        val info = pdf.inspect(bytes, "document.pdf")
        access.library(access.actor(email), write = true); templates.get(email, request.templateId)
        val filename = request.name.trim().replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(116).trim().removeSuffix(".pdf") + ".pdf"
        return view(documents.saveAndFlush(GeneratedDocument(ownerId = actor.id, templateId = template.id, templateRevision = template.revision,
            templateName = template.name, name = request.name.trim(), filename = filename, pageCount = info.pageCount, byteSize = bytes.size,
            valuesJson = mapper.writeValueAsString(values), templateSnapshot = template.templateJson, pdfBytes = bytes)))
    }
    fun page(email: String,id: UUID,page: Int) = delivery.page(email,page) {access.document(access.actor(email),id)}
    fun download(email: String,id: UUID) = delivery.download(email) {access.document(access.actor(email),id)}
    fun email(email: String,id: UUID,request: EmailDocumentRequest) = delivery.email(email,request) {access.document(access.actor(email),id)}
    fun view(saved: GeneratedDocument) = GeneratedDocumentView(saved.id, saved.ownerId, saved.templateId, saved.templateRevision, saved.templateName,
        saved.name, saved.filename, saved.pageCount, saved.byteSize, saved.createdAt)
}
