package com.starter.documents

import com.starter.dto.SendEmailRequest
import com.starter.email.EmailAttachment
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.notification.*
import com.starter.security.RateLimits
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.storage.StorageObjectDownload
import org.springframework.stereotype.Service

/** Both the private library and an authorized workflow step use the same PDF and delivery policy. */
@Service
class GeneratedDocumentDelivery(private val access: DocumentAccess,private val pdf: PdfService,private val policy: DocumentAccessService,
    private val notifications: NotificationService,private val rates: RateLimits) {
    fun page(email: String,page: Int,authorize: () -> GeneratedDocument): ByteArray {
        val actor=access.actor(email);val saved=authorize();val previous=policy.current()
        if (!previous.pdfPreviewEnabled) throw ForbiddenException("PDF viewing is disabled")
        rates.check("generated-preview",actor.id.toString(),120,300)
        val bytes=pdf.page(saved.pdfBytes,page,previous.watermarkText.takeIf {previous.downloadMode==DownloadMode.WATERMARKED},email)
        authorize();if (policy.current()!=previous) throw ForbiddenException("Document policy changed. Refresh the page.")
        return bytes
    }
    fun download(email: String,authorize: () -> GeneratedDocument): StorageObjectDownload {
        access.actor(email);val saved=authorize();val previous=policy.current()
        if (previous.downloadMode==DownloadMode.DISABLED) throw ForbiddenException("Downloads are disabled")
        val bytes=if (previous.downloadMode==DownloadMode.WATERMARKED) pdf.watermark(saved.pdfBytes,previous.watermarkText,email) else saved.pdfBytes
        authorize();if (policy.current()!=previous) throw ForbiddenException("Document policy changed. Refresh the page.")
        return StorageObjectDownload(saved.filename,"application/pdf",bytes)
    }
    fun email(email: String,request: EmailDocumentRequest,event: String="documents.email",authorize: () -> GeneratedDocument): com.starter.dto.EmailDeliveryResult {
        val actor=access.actor(email);rates.check("email-generated-document",actor.id.toString(),10,300)
        if (!notifications.availableFor(event,"email")) throw BadRequestException("Enable this document notification and configure email delivery first")
        if (request.protection==DocumentEmailProtection.PASSWORD && (request.password.length<12 || request.password.any {it.isISOControl()})) throw BadRequestException("Use a PDF password of at least 12 characters without control characters")
        val previous=policy.current();val file=download(email,authorize)
        val bytes=if (request.protection==DocumentEmailProtection.PASSWORD) pdf.protect(file.bytes,request.password) else file.bytes
        if (bytes.size>EmailAttachment.MAX_BYTES) throw BadRequestException("Protected PDF exceeds 10 MB")
        authorize();if (policy.current()!=previous) throw ForbiddenException("Document policy changed. Refresh before sending.")
        return notifications.sendEmail(event,SendEmailRequest(request.recipient,request.subject,request.text),listOf(EmailAttachment(file.filename,bytes)))
    }
}
