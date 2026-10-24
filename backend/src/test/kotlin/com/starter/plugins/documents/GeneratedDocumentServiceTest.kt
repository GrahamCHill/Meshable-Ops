package com.starter.plugins.documents

import com.starter.rendering.WeasyRendererClient

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.DocumentAccessPolicy
import com.starter.dto.EmailDeliveryResult
import com.starter.dto.SendEmailRequest
import com.starter.email.EmailAttachment
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.DownloadMode
import com.starter.model.postgres.User
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import com.starter.security.RateLimits
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.io.ByteArrayOutputStream

class GeneratedDocumentServiceTest {
    private val access = mock(DocumentAccess::class.java)
    private val policy = mock(DocumentAccessService::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val actor = User(email = "owner@example.test")
    private val bytes = PDDocument().use { doc -> doc.addPage(PDPage()); ByteArrayOutputStream().use { doc.save(it); it.toByteArray() } }
    private val document = GeneratedDocument(ownerId = actor.id, name = "Quote", filename = "quote.pdf", pdfBytes = bytes)
    private var attachments = emptyList<EmailAttachment>()
    private val service = GeneratedDocumentService(mock(GeneratedDocumentRepository::class.java), mock(DocumentTemplateService::class.java), access,
        DocumentFields(), mock(WeasyRendererClient::class.java), jacksonObjectMapper(), PdfService(), GeneratedDocumentDelivery(access,PdfService(),policy,notifications,mock(RateLimits::class.java)), mock(RateLimits::class.java))
    @BeforeEach fun setup() {
        `when`(access.actor(actor.email)).thenReturn(actor); `when`(access.document(actor, document.id)).thenReturn(document)
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
        `when`(notifications.availableFor("documents.email","email")).thenReturn(true)
        `when`(notifications.sendEmail(anyString(), any<SendEmailRequest>() ?: SendEmailRequest("a@b.test", "Subject", "Text"), anyList())).thenAnswer {
            attachments = it.getArgument(2); EmailDeliveryResult("ACCEPTED", "Accepted")
        }
    }
    @Test fun `generated PDFs can be emailed without a password as actual attachments`() {
        service.email(actor.email, document.id, EmailDocumentRequest("reader@example.test", "Quote", "Please find attached"))
        assertThat(attachments).hasSize(1); assertThat(attachments.single().bytes).isEqualTo(bytes)
        Loader.loadPDF(attachments.single().bytes).use { assertThat(it.isEncrypted).isFalse() }
    }
    @Test fun `password protected email encrypts the attachment without modifying the stored PDF`() {
        service.email(actor.email, document.id, EmailDocumentRequest("reader@example.test", "Quote", "Please find attached", DocumentEmailProtection.PASSWORD, "Sample-PDF-password-123"))
        assertThatThrownBy { Loader.loadPDF(attachments.single().bytes) }.isInstanceOf(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException::class.java)
        Loader.loadPDF(attachments.single().bytes, "Sample-PDF-password-123").use { assertThat(it.isEncrypted).isTrue() }
        assertThat(document.pdfBytes).isEqualTo(bytes)
    }
    @Test fun `disabled downloads block email and previews respect their own switch`() {
        `when`(policy.current()).thenReturn(DocumentAccessPolicy(downloadMode = DownloadMode.DISABLED))
        assertThatThrownBy { service.email(actor.email, document.id, EmailDocumentRequest("reader@example.test", "Quote", "Attached")) }.isInstanceOf(ForbiddenException::class.java)
        assertThat(attachments).isEmpty()
        `when`(policy.current()).thenReturn(DocumentAccessPolicy(pdfPreviewEnabled = false))
        assertThatThrownBy { service.page(actor.email, document.id, 1) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `policy changes after protection block sending the stale derivative`() {
        `when`(policy.current()).thenReturn(DocumentAccessPolicy(), DocumentAccessPolicy(), DocumentAccessPolicy(), DocumentAccessPolicy(downloadMode = DownloadMode.WATERMARKED))
        assertThatThrownBy { service.email(actor.email, document.id, EmailDocumentRequest("reader@example.test", "Quote", "Attached", DocumentEmailProtection.PASSWORD, "Sample-PDF-password-123")) }.isInstanceOf(ForbiddenException::class.java)
        assertThat(attachments).isEmpty()
    }
}
