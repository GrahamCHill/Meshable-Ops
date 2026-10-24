package com.starter.share

import com.starter.dto.SendEmailRequest
import com.starter.email.EmailDeliveryException
import com.starter.email.EmailAttachment
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.DownloadMode
import com.starter.notification.NotificationChannel
import com.starter.notification.NotificationService
import com.starter.repository.postgres.UserRepository
import com.starter.security.RateLimits
import com.starter.security.SecureValues
import com.starter.service.DocumentAccessService
import com.starter.service.PdfService
import com.starter.service.storage.StorageObjectDownload
import com.starter.service.storage.normalizeStorageObject
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class FileShareService(private val shares: FileShareRepository, private val grants: ShareAccessGrantRepository,
    private val settings: ShareSettingsService, private val users: UserRepository, private val files: ShareFileAccess,
    private val policy: DocumentAccessService, private val pdf: PdfService, private val passwords: PasswordEncoder,
    private val tokens: SecureValues, private val notifications: NotificationService, private val rates: RateLimits) {
    fun list() = shares.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).map(::view)

    // Persist PENDING before external delivery, then commit ACCEPTED/revoked separately.
    // Never hold a database transaction/connection open while sending the attachment.
    fun create(actor: String, request: CreateShareRequest): ShareCreated {
        val config = settings.settings()
        if (!config.enabled || !config.emailAvailable) throw BadRequestException("Enable file sharing and configure working email delivery first")
        if (request.lifetimeHours !in 1..config.maxLifetimeHours) throw BadRequestException("Choose an expiry within the configured sharing limit")
        if (policy.current().downloadMode == DownloadMode.DISABLED) throw ForbiddenException("Downloads are disabled; external sharing is unavailable")
        if (request.password.length < 12 || request.password.any { it.isISOControl() }) throw BadRequestException("Use a share password of at least 12 characters without control characters")
        val base = settings.baseUrl()
        val owner = users.findByEmail(actor).orElse(null)?.takeIf { it.active && it.role == "ADMIN" } ?: throw ForbiddenException("Administrator access required")
        rates.check("share-create-user", owner.id.toString(), 10, 300)
        val path = normalizeStorageObject(request.path)
        val source = files.read(request.folderId, path)
        val info = pdf.inspect(source.bytes, source.filename)
        val token = tokens.token()
        val shareId = UUID.randomUUID()
        val protectedPdf = pdf.watermarkAndProtect(source.bytes, "SHARED COPY", "${request.recipient} | $shareId", request.password)
        if (protectedPdf.size > EmailAttachment.MAX_BYTES) throw BadRequestException("The protected PDF exceeds the 10 MB email attachment limit")
        files.check(request.folderId)
        if (!settings.settings().enabled || policy.current().downloadMode == DownloadMode.DISABLED) throw ForbiddenException("File sharing is unavailable")
        val attachmentName = source.filename.map { if (it.isISOControl() || it in "\\\"/") '_' else it }.joinToString("").take(196).let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val share = shares.saveAndFlush(FileShare(id = shareId, tokenHash = SecureValues.hash(token), ownerId = owner.id, folderId = request.folderId,
            storagePath = path, filename = source.filename, recipient = request.recipient, passwordHash = passwords.encode(request.password),
            sourceHash = SecureValues.hash(source.bytes), pageCount = info.pageCount, expiresAt = Instant.now().plusSeconds(request.lifetimeHours * 3600L)))
        val url = "$base/shared/$token"
        val result = notifications.send(NotificationChannel.EMAIL, SendEmailRequest(request.recipient, "A protected PDF has been shared with you",
            "The attached PDF is watermarked and password protected: ${source.filename}\n\nAsk the sender for the password through a separate channel. The password is not included in this email.\n\nYou can also open it in the app:\n$url\nThis online link expires: ${share.expiresAt}. The attachment remains available after the link expires or is revoked.",), listOf(EmailAttachment(attachmentName, protectedPdf)))
        share.deliveryStatus = result.status
        if (result.status != "ACCEPTED") { share.revoked = true; shares.saveAndFlush(share); throw EmailDeliveryException("The share email could not be sent. The link has been revoked; create a new share after restoring delivery.") }
        shares.saveAndFlush(share)
        return ShareCreated(view(share), url)
    }
    fun revoke(id: UUID) { val share = shares.findById(id).orElseThrow { NotFoundException("Share not found") }; share.revoked = true; shares.saveAndFlush(share) }
    fun unlock(token: String, password: String, ip: String): ShareUnlocked {
        rates.check("share-password-ip", "$token:$ip", 10, 300)
        rates.check("share-password-link", token, 100, 3600)
        val share = current(token)
        if (!passwords.matches(password, share.passwordHash)) throw BadCredentialsException("Invalid share password")
        val raw = tokens.token(); val expires = minOf(Instant.now().plusSeconds(900), share.expiresAt)
        grants.saveAndFlush(ShareAccessGrant(SecureValues.hash(raw), share.id, expires))
        return ShareUnlocked(raw, expires)
    }
    fun metadata(token: String, access: String): SharedFileView {
        val share = authorized(token, access); source(share)
        val accessPolicy = policy.current()
        authorized(token, access)
        return SharedFileView(share.filename, share.pageCount, share.recipient, share.expiresAt,
            accessPolicy.pdfPreviewEnabled, accessPolicy.downloadMode != DownloadMode.DISABLED)
    }
    fun page(token: String, access: String, page: Int): ByteArray {
        val share = authorized(token, access)
        val previous = policy.current()
        if (!previous.pdfPreviewEnabled) throw ForbiddenException("PDF viewing is disabled")
        val result = pdf.page(source(share).bytes, page, "SHARED COPY", "${share.recipient} | ${share.id}")
        authorized(token, access)
        if (policy.current() != previous) throw ForbiddenException("Document policy changed. Refresh the page.")
        return result
    }
    fun download(token: String, access: String, password: String, ip: String): StorageObjectDownload {
        rates.check("share-download-password", "$token:$ip", 10, 300)
        val share = authorized(token, access)
        val previous = policy.current()
        if (previous.downloadMode == DownloadMode.DISABLED) throw ForbiddenException("Downloads are disabled")
        if (!passwords.matches(password, share.passwordHash)) throw BadCredentialsException("Invalid share password")
        val result = pdf.watermarkAndProtect(source(share).bytes, "SHARED COPY", "${share.recipient} | ${share.id}", password)
        authorized(token, access)
        if (policy.current() != previous) throw ForbiddenException("Document policy changed. Refresh the page.")
        return StorageObjectDownload(share.filename, "application/pdf", result)
    }
    private fun source(share: FileShare): StorageObjectDownload {
        val data = files.read(share.folderId, share.storagePath)
        if (!SecureValues.equal(SecureValues.hash(data.bytes), share.sourceHash)) throw NotFoundException("The shared file has changed. Request a new link.")
        return data
    }
    private fun authorized(token: String, access: String): FileShare {
        val share = current(token)
        val grant = grants.findById(SecureValues.hash(access)).orElse(null)
        if (access.isBlank() || grant == null || grant.shareId != share.id || !grant.expiresAt.isAfter(Instant.now())) throw BadCredentialsException("Unlock the shared file again")
        return share
    }
    private fun current(token: String): FileShare {
        if (!settings.settings().enabled) throw NotFoundException("Shared file unavailable")
        val share = shares.findByTokenHash(SecureValues.hash(token)).orElse(null) ?: throw NotFoundException("Shared file unavailable")
        if (share.revoked || share.deliveryStatus != "ACCEPTED" || !share.expiresAt.isAfter(Instant.now())) throw NotFoundException("Shared file unavailable")
        if (users.findById(share.ownerId).orElse(null)?.let { it.active && it.role == "ADMIN" } != true) throw NotFoundException("Shared file unavailable")
        files.check(share.folderId)
        return share
    }
    private fun view(s: FileShare) = ShareView(s.id, s.filename, s.recipient, s.createdAt, s.expiresAt, s.revoked, s.deliveryStatus)
    @Scheduled(fixedDelay = 3_600_000) fun purge() { grants.deleteByExpiresAtBefore(Instant.now()) }
}
