package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.model.postgres.User
import com.starter.security.RateLimits
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.web.client.RestTemplateBuilder
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue

class SupersetAccountTransportTest {
    @Test fun `real HTTP transport handles large account lookups without negotiating undecodable Brotli`() {
        val mapper = jacksonObjectMapper()
        val postedUsers = ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val requestBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val body = when (exchange.requestURI.path) {
                "/api/v1/security/login" -> """{"access_token":"transport-token"}"""
                "/api/v1/security/csrf_token/" -> """{"result":"csrf-token"}"""
                "/api/v1/security/roles/" -> """{"count":1,"result":[{"id":2,"name":"Alpha"}]}"""
                "/api/v1/security/users/" -> if (exchange.requestMethod == "POST") {
                    postedUsers.add(requestBody)
                    """{"id":42}"""
                } else mapper.writeValueAsString(mapOf("count" to 0, "result" to emptyList<Any>(), "padding" to "x".repeat(1024)))
                else -> error("Unexpected Superset path")
            }
            // Flask-Compress uses Brotli on larger FAB list responses. The live Java
            // transport did not decode them, even though it advertised compression.
            val compressed = body.length > 500 && exchange.requestHeaders.getFirst("Accept-Encoding") != "identity"
            val bytes = if (compressed) {
                exchange.responseHeaders.add("Content-Encoding", "br")
                Base64.getDecoder().decode("GygEoBwHdiz4hUpYHHwzQqU3CAvenhaB2epaOn3gkAPmQ/OKNCKtCo2GaEAkXfFCQtM7vY/nFhE=")
            } else body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val access = mock(AccessControlService::class.java)
            val actor = User(email = "author+transport@example.test", username = "author", role = "ADMIN")
            `when`(access.actor()).thenReturn(actor)
            val service = SupersetAccountService(access, mock(RateLimits::class.java), mock(AuditLogService::class.java), mapper,
                RestTemplateBuilder(), "http://127.0.0.1:${server.address.port}", "service-admin", "private-service-password")
            assertThat(service.setPassword("A personal analytics password")).isEqualTo(SupersetAccountResponse(actor.email, created = true))
            assertThat(postedUsers).hasSize(1)
            val created = mapper.readTree(postedUsers.single())
            assertThat(created.path("username").asText()).isEqualTo(actor.email)
            assertThat(created.path("roles").map { it.asInt() }).containsExactly(2)
        } finally { server.stop(0) }
    }
}
