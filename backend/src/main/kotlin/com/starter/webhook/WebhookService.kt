package com.starter.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.MessageResponse
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.security.RateLimits
import com.starter.security.SecretEncryption
import com.starter.security.SecureValues
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Semaphore

@Service
class WebhookService(private val endpoints: WebhookEndpointRepository, private val deliveries: WebhookDeliveryRepository,
    private val crypto: SecretEncryption, private val tokens: SecureValues, private val mapper: ObjectMapper,
    private val http: WebhookHttpClient, private val rates: RateLimits,
    @Value("\${app.public-base-url:}") private val publicBaseUrl: String) {
    private val workers = Semaphore(2)
    fun list() = endpoints.findAll().map(::view)
    fun save(id: UUID?, request: WebhookRequest): WebhookSaved {
        crypto.requireConfigured()
        val saved = id?.let(::endpoint) ?: WebhookEndpoint(direction = request.direction)
        if (saved.direction != request.direction) throw BadRequestException("Create a new endpoint to change its direction")
        val url = if (request.direction == WebhookDirection.OUTGOING) http.validate(request.url.trim()).toString() else ""
        val newSecret = request.secret?.ifBlank { null } ?: if (id == null || request.rotateSecret || url != saved.url) tokens.token() else null
        if (newSecret != null && (newSecret.length < 32 || newSecret.any { it.isISOControl() })) throw BadRequestException("Webhook secrets need at least 32 characters without control characters")
        saved.name = request.name.trim(); saved.enabled = request.enabled; saved.authentication = request.authentication; saved.url = url
        if (newSecret != null) saved.secret = newSecret
        endpoints.saveAndFlush(saved)
        return WebhookSaved(view(saved), newSecret)
    }
    fun history(id: UUID): List<WebhookDeliveryView> { endpoint(id); return deliveries.findByEndpointIdOrderByCreatedAtDesc(id, PageRequest.of(0, 50)).map {
        WebhookDeliveryView(it.id, it.createdAt, it.deliveryKey, it.status, it.httpStatus, it.payload)
    } }
    fun send(id: UUID, request: WebhookSendRequest): WebhookDeliveryView {
        val endpoint = endpoint(id)
        if (!endpoint.enabled || endpoint.direction != WebhookDirection.OUTGOING) throw BadRequestException("Enable an outgoing webhook first")
        if (!workers.tryAcquire()) throw BadRequestException("Webhook delivery is busy. Try again shortly.")
        try {
            rates.check("webhook-outgoing", id.toString(), 30, 300)
            val deliveryId = UUID.randomUUID().toString(); val timestamp = Instant.now().epochSecond.toString()
            val body = mapper.writeValueAsString(mapOf("id" to deliveryId, "event" to request.event, "createdAt" to Instant.now().toString(), "data" to request.data))
            if (body.toByteArray().size > 65536) throw BadRequestException("Webhook payloads must be 64 KB or smaller")
            val headers = mutableMapOf("X-Webhook-Id" to deliveryId, "X-Webhook-Timestamp" to timestamp)
            if (endpoint.authentication == WebhookAuthentication.HMAC) headers["X-Webhook-Signature"] = "sha256=" + SecureValues.hmac(endpoint.secret, "$timestamp.$deliveryId.$body")
            else headers["Authorization"] = "Bearer ${endpoint.secret}"
            val delivery = WebhookDelivery(endpointId = id, deliveryKey = deliveryId, payload = body, status = "FAILED")
            try { delivery.httpStatus = http.post(endpoint.url, headers, body); if (delivery.httpStatus in 200..299) delivery.status = "ACCEPTED" }
            catch (_: Exception) { /* Never retain remote response bodies, secrets, or transport exception details. */ }
            deliveries.saveAndFlush(delivery); deliveries.trim(id)
            return WebhookDeliveryView(delivery.id, delivery.createdAt, delivery.deliveryKey, delivery.status, delivery.httpStatus, body)
        } finally { workers.release() }
    }
    fun receive(id: UUID, body: String, authorization: String?, signature: String?, timestamp: String?, suppliedId: String?): MessageResponse {
        val endpoint = endpoint(id)
        if (!endpoint.enabled || endpoint.direction != WebhookDirection.INCOMING) throw NotFoundException("Webhook unavailable")
        if (body.toByteArray(Charsets.UTF_8).size > 65536) throw BadRequestException("Webhook payloads must be 64 KB or smaller")
        val deliveryId = suppliedId ?: UUID.randomUUID().toString()
        if (!Regex("[A-Za-z0-9._:-]{1,128}").matches(deliveryId)) throw BadRequestException("Invalid webhook delivery ID")
        val valid = if (endpoint.authentication == WebhookAuthentication.BEARER) SecureValues.equal(authorization.orEmpty(), "Bearer ${endpoint.secret}") else {
            val seconds = timestamp?.toLongOrNull()
            seconds != null && suppliedId != null && seconds >= Instant.now().epochSecond - 300 && seconds <= Instant.now().epochSecond + 300 &&
                SecureValues.equal(signature.orEmpty(), "sha256=" + SecureValues.hmac(endpoint.secret, "$timestamp.$deliveryId.$body"))
        }
        if (!valid) throw BadCredentialsException("Webhook authentication failed")
        rates.check("webhook-incoming-endpoint", id.toString(), 120, 300)
        try { mapper.readTree(body) ?: throw IllegalArgumentException() } catch (_: Exception) { throw BadRequestException("Send a valid JSON payload") }
        try { deliveries.saveAndFlush(WebhookDelivery(endpointId = id, deliveryKey = deliveryId, payload = body)) }
        catch (_: DataIntegrityViolationException) { return MessageResponse("Webhook accepted") }
        deliveries.trim(id)
        return MessageResponse("Webhook accepted")
    }
    private fun endpoint(id: UUID) = endpoints.findById(id).orElseThrow { NotFoundException("Webhook not found") }
    private fun view(e: WebhookEndpoint) = WebhookView(e.id, e.name, e.direction, e.enabled,
        if (e.direction == WebhookDirection.INCOMING) publicBaseUrl.trimEnd('/') + "/api/webhooks/incoming/${e.id}" else e.url, e.authentication, e.secret.isNotBlank())
    @Scheduled(fixedDelay = 3_600_000) fun purge() { deliveries.deleteByCreatedAtBefore(Instant.now().minusSeconds(7 * 86400)) }
}
