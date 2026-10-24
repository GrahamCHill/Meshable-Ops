package com.starter.webhook

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import com.starter.security.EncryptedSecretConverter
import jakarta.persistence.*
import jakarta.validation.constraints.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

enum class WebhookDirection { INCOMING, OUTGOING }
enum class WebhookAuthentication { HMAC, BEARER }
@Entity @Table(name = "webhook_endpoints")
class WebhookEndpoint(
    @Id val id: UUID = UUID.randomUUID(), var name: String = "",
    @Enumerated(EnumType.STRING) val direction: WebhookDirection = WebhookDirection.INCOMING,
    var enabled: Boolean = true,
    @Column(columnDefinition = "TEXT") var url: String = "",
    @Enumerated(EnumType.STRING) var authentication: WebhookAuthentication = WebhookAuthentication.HMAC,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class) @Column(columnDefinition = "TEXT") var secret: String = "",
) { override fun toString() = "WebhookEndpoint(id=$id, direction=$direction)" }
@Entity @Table(name = "webhook_deliveries", uniqueConstraints = [UniqueConstraint(columnNames = ["endpoint_id", "delivery_key"])])
class WebhookDelivery(
    @Id val id: UUID = UUID.randomUUID(), val endpointId: UUID = UUID.randomUUID(),
    val deliveryKey: String = "", val createdAt: Instant = Instant.now(),
    var status: String = "ACCEPTED", var httpStatus: Int? = null,
    @get:JsonIgnore @Convert(converter = EncryptedSecretConverter::class) @Column(columnDefinition = "TEXT") var payload: String? = null,
) { override fun toString() = "WebhookDelivery(id=$id, status=$status)" }
interface WebhookEndpointRepository : JpaRepository<WebhookEndpoint, UUID>
interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, UUID> {
    fun findByEndpointIdOrderByCreatedAtDesc(id: UUID, pageable: Pageable): List<WebhookDelivery>
    @Modifying @Transactional @Query(value = "DELETE FROM webhook_deliveries WHERE id IN (SELECT id FROM webhook_deliveries WHERE endpoint_id = :id ORDER BY created_at DESC OFFSET 100)", nativeQuery = true)
    fun trim(id: UUID)
    @Transactional fun deleteByCreatedAtBefore(before: Instant)
}
data class WebhookRequest(@field:NotBlank @field:Size(max = 120) val name: String, val direction: WebhookDirection,
    val enabled: Boolean = true, @field:Size(max = 2000) val url: String = "", val authentication: WebhookAuthentication = WebhookAuthentication.HMAC,
    @get:com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @field:Size(max = 4096) val secret: String? = null, val rotateSecret: Boolean = false) {
    override fun toString() = "WebhookRequest(direction=$direction)"
}
data class WebhookView(val id: UUID, val name: String, val direction: WebhookDirection, val enabled: Boolean, val url: String,
    val authentication: WebhookAuthentication, val secretConfigured: Boolean)
data class WebhookSaved(val endpoint: WebhookView, val secret: String? = null) { override fun toString() = "WebhookSaved()" }
data class WebhookSendRequest(@field:Pattern(regexp = "[A-Za-z0-9._-]{1,80}") val event: String = "manual.test", val data: JsonNode)
data class WebhookDeliveryView(val id: UUID, val createdAt: Instant, val deliveryKey: String, val status: String, val httpStatus: Int?, val payload: String?)
