package com.starter.webhook

import com.starter.exception.BadRequestException
import com.starter.security.RateLimits
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

@RestController @RequestMapping("/api/admin/webhooks") @PreAuthorize("hasRole('ADMIN')")
class AdminWebhookController(private val service: WebhookService) {
    @GetMapping fun list() = service.list()
    @PostMapping fun create(@Valid @RequestBody request: WebhookRequest) = service.save(null, request)
    @PutMapping("/{id}") fun update(@PathVariable id: UUID, @Valid @RequestBody request: WebhookRequest) = service.save(id, request)
    @GetMapping("/{id}/deliveries") fun history(@PathVariable id: UUID) = service.history(id)
    @PostMapping("/{id}/send") fun send(@PathVariable id: UUID, @Valid @RequestBody request: WebhookSendRequest) = service.send(id, request)
}
@RestController
class IncomingWebhookController(private val service: WebhookService, private val rates: RateLimits) {
    @PostMapping("/api/webhooks/incoming/{id}", consumes = ["application/json"])
    fun receive(@PathVariable id: UUID, request: HttpServletRequest): com.starter.dto.MessageResponse {
        rates.check("webhook-incoming-ip", request.remoteAddr, 180, 300)
        val bytes = request.inputStream.readNBytes(65537)
        if (bytes.size > 65536) throw BadRequestException("Webhook payloads must be 64 KB or smaller")
        val body = runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { throw BadRequestException("Use UTF-8 JSON") }
        return service.receive(id, body, request.getHeader("Authorization"), request.getHeader("X-Webhook-Signature"),
            request.getHeader("X-Webhook-Timestamp"), request.getHeader("X-Webhook-Id"))
    }
}
