package com.starter.webhook

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.security.*
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.BadCredentialsException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Optional

class WebhookTest {
    private val endpoints = mock(WebhookEndpointRepository::class.java)
    private val deliveries = mock(WebhookDeliveryRepository::class.java)
    private val endpoint = WebhookEndpoint(name = "Sample incoming", secret = "a".repeat(43))
    private val mapper = jacksonObjectMapper()
    private val service = WebhookService(endpoints, deliveries, SecretEncryption(java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 6 })), SecureValues("sample-pepper"), mapper, WebhookHttpClient(false), mock(RateLimits::class.java), "https://app.example.test")
    private val body = "{\"message\":\"example\"}"
    private fun prepare() { `when`(endpoints.findById(endpoint.id)).thenReturn(Optional.of(endpoint)) }
    private fun signature(timestamp: String, id: String = "delivery-1", payload: String = body) = "sha256=" + SecureValues.hmac(endpoint.secret, "$timestamp.$id.$payload")

    @Test fun `HMAC verifies original bytes signed id and timestamp before persisting`() {
        prepare(); val time = Instant.now().epochSecond.toString()
        service.receive(endpoint.id, body, null, signature(time), time, "delivery-1")
        val saved = ArgumentCaptor.forClass(WebhookDelivery::class.java)
        verify(deliveries).saveAndFlush(saved.capture()); assertThat(saved.value.payload).isEqualTo(body)
        listOf(
            { service.receive(endpoint.id, body + " ", null, signature(time), time, "delivery-1") },
            { service.receive(endpoint.id, body, null, signature(time), time, "changed-id") },
            { service.receive(endpoint.id, body, null, signature(time), time, null) },
            { val old = Instant.now().minusSeconds(301).epochSecond.toString(); service.receive(endpoint.id, body, null, signature(old), old, "delivery-1") },
            { val future = Instant.now().plusSeconds(301).epochSecond.toString(); service.receive(endpoint.id, body, null, signature(future), future, "delivery-1") },
        ).forEach { assertThatThrownBy { it() }.isInstanceOf(BadCredentialsException::class.java) }
        verify(deliveries, times(1)).saveAndFlush(org.mockito.ArgumentMatchers.any(WebhookDelivery::class.java))
    }
    @Test fun `disabled endpoint refuses requests and duplicate delivery ids are acknowledged without new work`() {
        prepare(); val time = Instant.now().epochSecond.toString()
        doThrow(DataIntegrityViolationException("duplicate delivery")).`when`(deliveries).saveAndFlush(org.mockito.ArgumentMatchers.any(WebhookDelivery::class.java))
        assertThat(service.receive(endpoint.id, body, null, signature(time), time, "delivery-1").message).isEqualTo("Webhook accepted")
        verify(deliveries, never()).trim(endpoint.id)
        endpoint.enabled = false
        assertThatThrownBy { service.receive(endpoint.id, body, null, signature(time), time, "delivery-1") }.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `Bearer authentication requires exact secret and JSON is size bounded`() {
        prepare(); endpoint.authentication = WebhookAuthentication.BEARER
        assertThatThrownBy { service.receive(endpoint.id, body, "Bearer wrong", null, null, "id-1") }.isInstanceOf(BadCredentialsException::class.java)
        service.receive(endpoint.id, body, "Bearer ${endpoint.secret}", null, null, "id-1")
        assertThatThrownBy { service.receive(endpoint.id, "x".repeat(65537), "Bearer ${endpoint.secret}", null, null, "id-2") }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.receive(endpoint.id, "not json", "Bearer ${endpoint.secret}", null, null, "id-2") }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `webhook target validation blocks private addresses unsafe schemes and credential-bearing URLs`() {
        val client = WebhookHttpClient(false)
        // Construct private-range fixtures from bytes: this test performs no DNS or external requests.
        val ipv4 = listOf(listOf(127,0,0,1),listOf(10,0,0,1),listOf(172,16,0,1),listOf(192,168,1,1),listOf(169,254,169,254),listOf(0,0,0,0),listOf(100,64,0,1),listOf(198,18,0,1),listOf(224,0,0,1))
        val ipv6 = listOf(ByteArray(16).apply {this[15]=1},ByteArray(16).apply {this[0]=0xfc.toByte();this[15]=1},ByteArray(16).apply {this[0]=0xfe.toByte();this[1]=0x80.toByte();this[15]=1})
        (ipv4.map { bytes -> bytes.map(Int::toByte).toByteArray() } + ipv6).forEach {
            assertThat(client.blocked(InetAddress.getByAddress(it))).isTrue()
        }
        assertThat(client.blocked(InetAddress.getByAddress(byteArrayOf(203.toByte(),0,113,1)))).isFalse()
        assertThatThrownBy { client.resolve("127.0.0.1") }.isInstanceOf(java.net.UnknownHostException::class.java) // NOSONAR: deliberate loopback rejection fixture.
        listOf("http://example.com", "file:///tmp/test", "https://user:pass@example.com", "https://example.com?secret=a", "https://example.com#fragment").forEach { url ->
            assertThatThrownBy { client.validate(url) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThat(client.validate("https://example.com/hook").host).isEqualTo("example.com")
    }
    @Test fun `outgoing signed payload is sent once without following redirects or leaking secrets`() {
        var captured = ""; var timestamp = ""; var delivery = ""; var signature = ""; var redirected = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/hook") { exchange ->
            captured = exchange.requestBody.bufferedReader().readText(); timestamp = exchange.requestHeaders.getFirst("X-Webhook-Timestamp")
            delivery = exchange.requestHeaders.getFirst("X-Webhook-Id"); signature = exchange.requestHeaders.getFirst("X-Webhook-Signature")
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/stolen"); exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/stolen") { exchange -> redirected++; exchange.sendResponseHeaders(200, -1); exchange.close() }
        server.start()
        try {
            val outgoing = WebhookEndpoint(direction = WebhookDirection.OUTGOING, secret = endpoint.secret, url = "http://127.0.0.1:${server.address.port}/hook")
            `when`(endpoints.findById(outgoing.id)).thenReturn(Optional.of(outgoing))
            val local = WebhookService(endpoints, deliveries, mock(SecretEncryption::class.java), SecureValues("pepper"), mapper, WebhookHttpClient(true), mock(RateLimits::class.java), "")
            val result = local.send(outgoing.id, WebhookSendRequest("file.shared", mapper.readTree(body)))
            assertThat(result.status).isEqualTo("FAILED"); assertThat(result.httpStatus).isEqualTo(302); assertThat(redirected).isZero()
            assertThat(signature).isEqualTo("sha256=" + SecureValues.hmac(endpoint.secret, "$timestamp.$delivery.$captured"))
            assertThat(mapper.readTree(captured).path("event").asText()).isEqualTo("file.shared")
        } finally { server.stop(0) }
    }
}
