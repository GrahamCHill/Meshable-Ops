package com.starter.email

import com.starter.dto.EmailDeliveryResult
import com.starter.dto.SendEmailRequest
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Service
import java.util.concurrent.Semaphore

/** Optional application mail: callers can inspect the result without making their workflow depend on a mail server. */
@Service
class EmailDeliveryService(private val settings: EmailSettingsService, private val provider: EmailProviderClient) {
    private val workers = Semaphore(2)

    fun send(message: SendEmailRequest, attachments: List<EmailAttachment> = emptyList()): EmailDeliveryResult {
        validateMailbox(message.recipient, "recipient")
        if (message.subject.isBlank() || message.subject.length > 200 || message.subject.any { it.isISOControl() })
            throw BadRequestException("Enter a subject of 1–200 characters without control characters")
        if (message.text.isBlank() || message.text.length > 20000) throw BadRequestException("Enter a message of 1–20000 characters")
        if (attachments.size > 1 || attachments.any { it.bytes.isEmpty() || it.bytes.size > EmailAttachment.MAX_BYTES ||
                !it.filename.endsWith(".pdf", true) || it.filename.length > 200 || it.filename.any { c -> c.isISOControl() || c in "\\\"/" } })
            throw BadRequestException("Attach one PDF of 10 MB or smaller with a valid filename")
        if (!workers.tryAcquire()) return EmailDeliveryResult("UNAVAILABLE", "Email delivery is busy. Try again shortly.")
        try {
            val config = settings.current()
            if (!config.enabled) return EmailDeliveryResult("DISABLED", "Email delivery is disabled")
            provider.send(config, message, attachments)
            return EmailDeliveryResult("ACCEPTED", "Message accepted by the email provider. Inbox delivery is not confirmed.")
        } catch (failure: EmailDeliveryException) { return EmailDeliveryResult("UNAVAILABLE", failure.message ?: "Email delivery failed") }
          catch (_: BadRequestException) { return EmailDeliveryResult("UNAVAILABLE", "Email settings are incomplete or invalid. Check Administration > Email delivery.") }
          catch (_: Exception) { return EmailDeliveryResult("UNAVAILABLE", "Email delivery is unavailable. Check Administration > Email delivery.") }
        finally { workers.release() }
    }
}
