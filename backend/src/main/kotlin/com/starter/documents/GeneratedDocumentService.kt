package com.starter.documents

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.SendEmailRequest
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.DownloadMode
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import com.starter.security.RateLimits
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.storage.StorageObjectDownload
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GeneratedDocumentService(private val documents: GeneratedDocumentRepository, private val templates: DocumentTemplateService,
    private val access: DocumentAccess, private val fields: DocumentFields, private val renderer: WeasyRendererClient,
    private val mapper: ObjectMapper, private val pdf: PdfService, private val policy: DocumentAccessService,
    private val notifications: NotificationService, private val rates: RateLimits) {
    fun list(email: String): List<GeneratedDocumentView> { val actor = access.actor(email); return documents.visible(actor.id, actor.role == "ADMIN", PageRequest.of(0, 100)) }
    fun generate(email: String, request: GenerateDocumentRequest): GeneratedDocumentView {
        val actor = access.actor(email); rates.check("generate-document", actor.id.toString(), 20, 3600)
        val template = templates.get(email, request.templateId); val source = mapper.readTree(template.templateJson)
        val values = fields.resolve(source, templates.rules(template), request.values)
        val bytes = renderer.render(source, values)
        if (bytes.size > EmailAttachment.MAX_BYTES) throw BadRequestException("Generated PDF exceeds 10 MB")
        val info = pdf.inspect(bytes, "document.pdf")
        access.actor(email); templates.get(email, request.templateId)
        val filename = request.name.trim().replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(116).trim().removeSuffix(".pdf") + ".pdf"
        return view(documents.saveAndFlush(GeneratedDocument(ownerId = actor.id, templateId = template.id, templateRevision = template.revision,
            templateName = template.name, name = request.name.trim(), filename = filename, pageCount = info.pageCount, byteSize = bytes.size,
            valuesJson = mapper.writeValueAsString(values), templateSnapshot = template.templateJson, pdfBytes = bytes)))
    }
    fun page(email: String, id: UUID, page: Int): ByteArray {
        val actor = access.actor(email); val saved = access.document(actor, id); val previous = policy.current()
        if (!previous.pdfPreviewEnabled) throw ForbiddenException("PDF viewing is disabled")
        rates.check("generated-preview", actor.id.toString(), 120, 300)
        val bytes = pdf.page(saved.pdfBytes, page, previous.watermarkText.takeIf { previous.downloadMode == DownloadMode.WATERMARKED }, email)
        access.document(access.actor(email), id)
        if (policy.current() != previous) throw ForbiddenException("Document policy changed. Refresh the page.")
        return bytes
    }
    fun download(email: String, id: UUID): StorageObjectDownload {
        val saved = access.document(access.actor(email), id); val previous = policy.current()
        if (previous.downloadMode == DownloadMode.DISABLED) throw ForbiddenException("Downloads are disabled")
        val bytes = if (previous.downloadMode == DownloadMode.WATERMARKED) pdf.watermark(saved.pdfBytes, previous.watermarkText, email) else saved.pdfBytes
        access.document(access.actor(email), id)
        if (policy.current() != previous) throw ForbiddenException("Document policy changed. Refresh the page.")
        return StorageObjectDownload(saved.filename, "application/pdf", bytes)
    }
    fun email(email: String, id: UUID, request: EmailDocumentRequest): com.starter.dto.EmailDeliveryResult {
        val actor = access.actor(email)
        rates.check("email-generated-document", actor.id.toString(), 10, 300)
        if (!notifications.available(NotificationChannel.EMAIL)) throw BadRequestException("Configure email delivery first")
        if (request.protection == DocumentEmailProtection.PASSWORD && (request.password.length < 12 || request.password.any { it.isISOControl() })) throw BadRequestException("Use a PDF password of at least 12 characters without control characters")
        val previous = policy.current()
        val file = download(email, id)
        val bytes = if (request.protection == DocumentEmailProtection.PASSWORD) pdf.protect(file.bytes, request.password) else file.bytes
        if (bytes.size > EmailAttachment.MAX_BYTES) throw BadRequestException("Protected PDF exceeds 10 MB")
        access.document(access.actor(email), id)
        if (policy.current() != previous) throw ForbiddenException("Document policy changed. Refresh before sending.")
        return notifications.send(NotificationChannel.EMAIL, SendEmailRequest(request.recipient, request.subject, request.text), listOf(EmailAttachment(file.filename, bytes)))
    }
    fun view(saved: GeneratedDocument) = GeneratedDocumentView(saved.id, saved.ownerId, saved.templateId, saved.templateRevision, saved.templateName,
        saved.name, saved.filename, saved.pageCount, saved.byteSize, saved.createdAt)
}
