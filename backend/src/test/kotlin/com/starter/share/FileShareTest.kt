package com.starter.share

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.starter.dto.DocumentAccessPolicy
import com.starter.dto.EmailDeliveryResult
import com.starter.email.EmailAttachment
import com.starter.email.EmailDeliveryException
import com.starter.exception.*
import com.starter.model.postgres.DownloadMode
import com.starter.model.postgres.User
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import com.starter.repository.postgres.UserRepository
import com.starter.security.*
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.storage.StorageObjectDownload
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.security.authentication.BadCredentialsException
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Optional
import java.util.UUID

class FileShareTest {
    private val shares = mock(FileShareRepository::class.java)
    private val grants = mock(ShareAccessGrantRepository::class.java)
    private val settings = mock(ShareSettingsService::class.java)
    private val users = mock(UserRepository::class.java)
    private val files = mock(ShareFileAccess::class.java)
    private val policy = mock(DocumentAccessService::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val passwordEncoder = SaltedPasswordEncoder()
    private val owner = User(email = "admin@example.test", role = "ADMIN")
    private val folderId = UUID.randomUUID()
    private val request = CreateShareRequest(folderId, "contract.pdf", "recipient@example.test", "Sample-PDF-Password-123")
    private val pdfBytes = PDDocument().use { document -> document.addPage(PDPage()); ByteArrayOutputStream().use { document.save(it); it.toByteArray() } }
    private var saved: FileShare? = null
    private val grantRows = mutableMapOf<String, ShareAccessGrant>()
    private var attached: List<EmailAttachment> = emptyList()
    private var emailText = ""
    private var deliveryStatus = "ACCEPTED"
    private val service = FileShareService(shares, grants, settings, users, files, policy, PdfService(), passwordEncoder, SecureValues("test-pepper"), notifications, mock(RateLimits::class.java))
    @BeforeEach fun setup() {
        `when`(settings.settings()).thenReturn(ShareSettingsView(true, "https://app.example.test", 168, true))
        `when`(settings.baseUrl()).thenReturn("https://app.example.test")
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
        `when`(users.findByEmail(owner.email)).thenReturn(Optional.of(owner)); `when`(users.findById(owner.id)).thenReturn(Optional.of(owner))
        `when`(files.read(folderId, "contract.pdf")).thenReturn(StorageObjectDownload("contract.pdf", "application/pdf", pdfBytes))
        `when`(shares.saveAndFlush(any(FileShare::class.java))).thenAnswer { saved = it.getArgument(0); saved }
        `when`(shares.findByTokenHash(anyString())).thenAnswer { Optional.ofNullable(saved?.takeIf { row -> row.tokenHash == it.getArgument<String>(0) }) }
        `when`(shares.findById(any(UUID::class.java))).thenAnswer { Optional.ofNullable(saved?.takeIf { row -> row.id == it.getArgument<UUID>(0) }) }
        `when`(grants.saveAndFlush(any(ShareAccessGrant::class.java))).thenAnswer { val value = it.getArgument<ShareAccessGrant>(0); grantRows[value.id] = value; value }
        `when`(grants.findById(anyString())).thenAnswer { Optional.ofNullable(grantRows[it.getArgument(0)]) }
        `when`(notifications.sendEmail(anyString(), any<com.starter.dto.SendEmailRequest>() ?: com.starter.dto.SendEmailRequest("a@b.test", "subject", "text"), anyList())).thenAnswer {
            emailText = it.getArgument<com.starter.dto.SendEmailRequest>(1).text; attached = it.getArgument(2)
            EmailDeliveryResult(deliveryStatus, "Test result")
        }
    }
    @Test fun `email carries a password protected watermarked PDF and excludes the password from message text`() {
        val result = service.create(owner.email, request)
        assertThat(attached).hasSize(1); assertThat(attached.single().filename).isEqualTo("contract.pdf")
        assertThat(emailText).contains(result.url, "attached PDF", "attachment remains available").doesNotContain(request.password)
        assertThatThrownBy { Loader.loadPDF(attached.single().bytes) }.isInstanceOf(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException::class.java)
        Loader.loadPDF(attached.single().bytes, request.password).use { assertThat(PDFTextStripper().getText(it).replace(Regex("\\s+"), "")).contains("SHAREDCOPY", request.recipient) }
        assertThat(saved!!.passwordHash).startsWith("$2"); assertThat(passwordEncoder.matches(request.password, saved!!.passwordHash)).isTrue()
        assertThat(saved!!.tokenHash).doesNotContain(result.url.substringAfterLast('/'))
        assertThat(saved.toString()).doesNotContain(request.password)
    }
    @Test fun `links require password and matching short lived grant and can be revoked`() {
        val result = service.create(owner.email, request); val token = result.url.substringAfterLast('/')
        assertThatThrownBy { service.unlock(token, "wrong", "127.0.0.1") }.isInstanceOf(BadCredentialsException::class.java)
        val grant = service.unlock(token, request.password, "127.0.0.1")
        assertThat(grantRows.keys).doesNotContain(grant.accessToken)
        assertThat(grant.expiresAt).isBefore(Instant.now().plusSeconds(901))
        assertThatThrownBy { service.metadata(token, "wrong") }.isInstanceOf(BadCredentialsException::class.java)
        assertThat(service.metadata(token, grant.accessToken).filename).isEqualTo("contract.pdf")
        service.revoke(result.share.id)
        assertThatThrownBy { service.metadata(token, grant.accessToken) }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `source changes owner suspension visibility and download policy all block delivery`() {
        val result = service.create(owner.email, request); val token = result.url.substringAfterLast('/'); val grant = service.unlock(token, request.password, "127.0.0.1").accessToken
        `when`(policy.current()).thenReturn(DocumentAccessPolicy(downloadMode = DownloadMode.DISABLED))
        assertThatThrownBy { service.download(token, grant, request.password, "127.0.0.1") }.isInstanceOf(ForbiddenException::class.java)
        `when`(policy.current()).thenReturn(DocumentAccessPolicy())
        owner.active = false
        assertThatThrownBy { service.metadata(token, grant) }.isInstanceOf(NotFoundException::class.java)
        owner.active = true
        `when`(files.read(folderId, "contract.pdf")).thenReturn(StorageObjectDownload("contract.pdf", "application/pdf", byteArrayOf(1)))
        assertThatThrownBy { service.metadata(token, grant) }.isInstanceOf(NotFoundException::class.java).hasMessageContaining("changed")
        doThrow(NotFoundException("Unavailable")).`when`(files).check(folderId)
        assertThatThrownBy { service.metadata(token, grant) }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `disabled sharing and failed delivery do not leave usable new links`() {
        `when`(settings.settings()).thenReturn(ShareSettingsView(false, "https://app.example.test", 168, true))
        assertThatThrownBy { service.create(owner.email, request) }.isInstanceOf(BadRequestException::class.java)
        verifyNoInteractions(files)
        `when`(settings.settings()).thenReturn(ShareSettingsView(true, "https://app.example.test", 168, true))
        deliveryStatus = "UNAVAILABLE"
        assertThatThrownBy { service.create(owner.email, request) }.isInstanceOf(EmailDeliveryException::class.java)
        assertThat(saved!!.revoked).isTrue()
    }
    @Test fun `expired link and grant from another share cannot access a file`() {
        val result = service.create(owner.email, request); val token = result.url.substringAfterLast('/'); val grant = service.unlock(token, request.password, "127.0.0.1").accessToken
        grantRows[SecureValues.hash(grant)] = ShareAccessGrant(SecureValues.hash(grant), UUID.randomUUID(), Instant.now().plusSeconds(900))
        assertThatThrownBy { service.metadata(token, grant) }.isInstanceOf(BadCredentialsException::class.java)
        saved = FileShare(tokenHash = saved!!.tokenHash, ownerId = owner.id, expiresAt = Instant.now().minusSeconds(1), deliveryStatus = "ACCEPTED")
        assertThatThrownBy { service.unlock(token, request.password, "127.0.0.1") }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `share password is write only in JSON and cannot leak from DTO serialization`() {
        val mapper = jacksonObjectMapper()
        val input = """{"folderId":"$folderId","path":"contract.pdf","recipient":"recipient@example.test","password":"${request.password}"}"""
        assertThat(mapper.readValue<CreateShareRequest>(input).password).isEqualTo(request.password)
        assertThat(mapper.writeValueAsString(request)).doesNotContain(request.password, "password")
        assertThat(mapper.readValue<SharePasswordRequest>("""{"password":"${request.password}"}""").password).isEqualTo(request.password)
    }
}
