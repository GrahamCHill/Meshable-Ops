package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.controller.SupersetPasswordRequest
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.security.RateLimits
import com.starter.security.TooManyRequestsException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class SupersetAccountServiceTest {
    private val access = mock(AccessControlService::class.java)
    private val rates = mock(RateLimits::class.java)
    private val audit = mock(AuditLogService::class.java)
    private val mapper = jacksonObjectMapper()
    private lateinit var server: MockRestServiceServer
    private lateinit var service: SupersetAccountService
    private val actor = User(email = "author+reports@example.test", username = "author", name = "Test Author", role = "ADMIN")
    private val password = "My new analytics password!"
    private val url = "http://superset:8088"

    @BeforeEach fun setup() {
        service = SupersetAccountService(access, rates, audit, mapper,
            RestTemplateBuilder().additionalCustomizers(RestTemplateCustomizer { server = MockRestServiceServer.bindTo(it).build() }),
            url, "service-admin", "private-service-password")
        `when`(access.actor()).thenReturn(actor)
    }
    private fun login() {
        server.expect(requestTo("$url/api/v1/security/login")).andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"access_token":"service-token"}""", MediaType.APPLICATION_JSON))
    }
    private fun find(resource: String, column: String, value: String, result: List<Any>) {
        server.expect { request ->
            assertThat(request.method).isEqualTo(HttpMethod.GET)
            assertThat(request.uri.path).isEqualTo("/api/v1/security/$resource/")
            val query = URLDecoder.decode(request.uri.rawQuery.substringAfter("q="), StandardCharsets.UTF_8)
            val quoted = "'" + value.replace("!", "!!").replace("'", "!'") + "'"
            assertThat(query).isEqualTo("(filters:!((col:$column,opr:eq,value:$quoted)),page_size:2)")
            assertThat(request.headers.getFirst("Authorization")).isEqualTo("Bearer service-token")
        }.andRespond(withSuccess(mapper.writeValueAsString(mapOf("count" to result.size, "result" to result)), MediaType.APPLICATION_JSON))
    }
    private fun matches(result: List<Any> = emptyList()) {
        find("users", "username", actor.email, result)
        find("users", "email", actor.email, result)
    }
    private fun account(patch: Map<String, Any> = emptyMap()) = mapOf("id" to 42, "username" to actor.email,
        "email" to actor.email, "active" to true, "roles" to listOf(mapOf("id" to 2, "name" to "Alpha"))) + patch
    private fun csrf() {
        server.expect(requestTo("$url/api/v1/security/csrf_token/")).andRespond(withSuccess("""{"result":"csrf-token"}""", MediaType.APPLICATION_JSON))
    }

    @Test fun `first setup creates an email login with only Alpha authoring access`() {
        login(); matches(); find("roles", "name", "Alpha", listOf(mapOf("id" to 2, "name" to "Alpha"))); csrf()
        server.expect(requestTo("$url/api/v1/security/users/")).andExpect(method(HttpMethod.POST))
            .andExpect(header("X-CSRFToken", "csrf-token"))
            .andExpect(content().json(mapper.writeValueAsString(mapOf("username" to actor.email, "email" to actor.email,
                "first_name" to "Test", "last_name" to "Author", "password" to password, "active" to true, "roles" to listOf(2))), true))
            .andRespond(withSuccess("""{"id":42}""", MediaType.APPLICATION_JSON))
        val result = service.setPassword(password)
        assertThat(result.username).isEqualTo(actor.email)
        assertThat(result.created).isTrue()
        assertThat(mapper.writeValueAsString(result)).doesNotContain(password, "service-token", "private-service-password")
        verify(audit).record(actor.email, "SUPERSET_ACCOUNT_CREATED", "USER", actor.id.toString())
        server.verify()
    }
    @Test fun `reset updates the existing id without changing profile roles or dashboard ownership`() {
        login(); matches(listOf(account())); csrf()
        server.expect(requestTo("$url/api/v1/security/users/42")).andExpect(method(HttpMethod.PUT))
            .andExpect(content().json(mapper.writeValueAsString(mapOf("password" to password)), true))
            .andRespond(withSuccess(mapper.writeValueAsString(mapOf("result" to mapOf("password" to password))), MediaType.APPLICATION_JSON))
        assertThat(service.setPassword(password)).isEqualTo(SupersetAccountResponse(actor.email, created = false))
        verify(audit).record(actor.email, "SUPERSET_PASSWORD_CHANGED", "USER", actor.id.toString())
        server.verify()
    }
    @Test fun `legacy shared service email is freed while retaining the service login password`() {
        login(); matches(listOf(account(mapOf("username" to "service-admin", "roles" to listOf(mapOf("id" to 1, "name" to "Admin"))))))
        find("roles", "name", "Alpha", listOf(mapOf("id" to 2, "name" to "Alpha"))); csrf()
        server.expect(requestTo("$url/api/v1/security/users/42")).andExpect(method(HttpMethod.PUT))
            .andExpect(content().json("""{"email":"superset-service-42@accounts.invalid","password":"private-service-password"}""", true))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$url/api/v1/security/users/")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.username").value(actor.email)).andExpect(jsonPath("$.password").value(password))
            .andExpect(jsonPath("$.roles[0]").value(2))
            .andRespond(withSuccess("""{"id":43}""", MediaType.APPLICATION_JSON))
        assertThat(service.setPassword(password).created).isTrue()
        server.verify()
    }
    @Test fun `super admins can provision a personal login`() {
        `when`(access.actor()).thenReturn(actor.copy(role = "SUPER_ADMIN"))
        login(); matches(listOf(account())); csrf()
        server.expect(requestTo("$url/api/v1/security/users/42")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        assertThat(service.setPassword(password).username).isEqualTo(actor.email)
        server.verify()
    }
    @Test fun `ordinary members inactive admins and unauthenticated callers cannot touch Superset`() {
        listOf(actor.copy(role = "USER"), actor.copy(role = "BUSINESS_MANAGER"), actor.copy(role = "EXTERNAL"), actor.copy(active = false), null).forEach {
            `when`(access.actor()).thenReturn(it)
            assertThatThrownBy { service.setPassword(password) }.isInstanceOf(ForbiddenException::class.java)
        }
        verifyNoInteractions(rates, audit); server.verify()
    }
    @Test fun `admins without authoring permission cannot reset a password`() {
        doThrow(ForbiddenException("Unavailable")).`when`(access).requireFeature("admin.superset")
        assertThatThrownBy { service.setPassword(password) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(rates, audit); server.verify()
    }
    @Test fun `rate limited resets do not call Superset`() {
        doThrow(TooManyRequestsException()).`when`(rates).check("superset-password", actor.id.toString(), 5, 300)
        assertThatThrownBy { service.setPassword(password) }.isInstanceOf(TooManyRequestsException::class.java)
        server.verify()
    }
    @Test fun `invalid password lengths are rejected without calling Superset`() {
        listOf("short", " ".repeat(14), "a".repeat(129)).forEach {
            assertThatThrownBy { service.setPassword(it) }.isInstanceOf(BadRequestException::class.java)
        }
        verifyNoInteractions(rates, audit); server.verify()
    }
    @Test fun `disabled privileged or mismatched Superset accounts are never overwritten`() {
        listOf(mapOf("active" to false), mapOf("roles" to listOf(mapOf("id" to 1, "name" to "Admin"))),
            mapOf("username" to "service-admin"), mapOf("email" to "someone-else@example.test")).forEach { patch ->
            server.reset(); login(); matches(listOf(account(patch)))
            assertThatThrownBy { service.setPassword(password) }.isInstanceOf(ConflictException::class.java)
            server.verify()
        }
        verifyNoInteractions(audit)
    }
    @Test fun `different username and email matches are rejected`() {
        login(); find("users", "username", actor.email, listOf(account()))
        find("users", "email", actor.email, listOf(account(mapOf("id" to 43, "username" to "another-author"))))
        assertThatThrownBy { service.setPassword(password) }.isInstanceOf(ConflictException::class.java)
        server.verify()
    }
    @Test fun `service account username cannot be reset even if it is an email`() {
        `when`(access.actor()).thenReturn(actor.copy(email = "service-admin"))
        assertThatThrownBy { service.setPassword(password) }.isInstanceOf(ConflictException::class.java)
        verifyNoInteractions(rates, audit); server.verify()
    }
    @Test fun `Superset failures never expose response bodies or password requests`() {
        login(); matches(listOf(account())); csrf()
        server.expect(requestTo("$url/api/v1/security/users/42"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("password=$password private-service-password service-token"))
        assertThatThrownBy { service.setPassword(password) }.isInstanceOf(BadRequestException::class.java)
            .hasMessageNotContaining(password).hasMessageNotContaining("private-service-password").hasNoCause()
        assertThat(SupersetPasswordRequest(password).toString()).doesNotContain(password)
        verifyNoInteractions(audit); server.verify()
    }
    @Test fun `malformed Superset responses cannot leak passwords through JSON exceptions`() {
        login(); matches(listOf(account())); csrf()
        server.expect(requestTo("$url/api/v1/security/users/42")).andRespond(withSuccess("broken $password", MediaType.APPLICATION_JSON))
        assertThatThrownBy { service.setPassword(password) }.isInstanceOf(BadRequestException::class.java).hasMessageNotContaining(password).hasNoCause()
        server.verify()
    }
}
