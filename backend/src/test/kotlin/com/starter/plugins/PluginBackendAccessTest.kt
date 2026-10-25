package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.access.AccessControlService
import com.starter.access.AccessPolicy
import com.starter.exception.*
import com.starter.model.postgres.User
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.plugins.integrations.IntegrationResponse
import com.starter.plugins.packages.*
import com.starter.plugins.packages.backend.*
import com.starter.repository.postgres.UserRepository
import com.starter.security.RateLimits
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class PluginBackendAccessTest {
    private val json = jacksonObjectMapper()
    private val catalogue = mock(InstalledPluginCatalogue::class.java)
    private val registry = mock(PluginRegistry::class.java)
    private val controls = mock(AccessControlService::class.java)
    private val users = mock(UserRepository::class.java)
    private val config = mock(PluginConfigurationService::class.java)
    private val tickets = mock(BackendInvocations::class.java)
    private val worker = mock(PluginWorkerClient::class.java)
    private val backend = PluginBackendService(catalogue, registry, controls, users, config, tickets, worker, json, mock(RateLimits::class.java))
    private val records = mock(PluginRecordService::class.java)
    private val http = mock(IntegrationHttpClient::class.java)
    private val shared = mock(com.starter.plugins.shared.PluginSharedSettingsService::class.java)
    private val broker = PluginHostBroker(backend, records, config, http, json, shared)
    private val user = User(email = "member@example.test")
    private val record = PluginPackageRecord("zip-code", sha256 = "immutable-package")
    private val policy = AccessPolicy()
    private fun configure(permissions: Set<String> = emptySet(), method: String = "POST") {
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        `when`(controls.policy()).thenReturn(policy)
        `when`(controls.features(user, policy)).thenReturn(setOf("plugin.zip-code"))
        `when`(catalogue.find("zip-code")).thenReturn(record)
        `when`(catalogue.descriptor(record)).thenReturn(PluginPackageDescriptor(PluginManifest("zip-code", "Code", "1.0.0", "Test code",
            settingsFields = listOf(PluginSettingField("api-key", "API key", PluginFieldType.SECRET))), backend = PluginBackend(
            mapOf("main" to PluginBackendModule(BackendRuntime.PYTHON, "backend/main.py")),
            listOf(PluginBackendRoute("/run", "main", setOf(method), permissions = permissions)), setOf("https://example.com"))))
        `when`(tickets.claim("ticket")).thenReturn(InvocationScope("zip-code", user.id, record.sha256, "/run", method))
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `backend invocation checks current account features route role and package hash`() {
        configure()
        assertThat(backend.authorize("zip-code", "/run", "POST", user).actor.id).isEqualTo(user.id)
        assertThatThrownBy { backend.authorize("zip-code", "/admin", "POST", user) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { backend.authorize("zip-code", "/run", "GET", user) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { backend.authorize("zip-code", "/run", "POST", user, "different-package") }.isInstanceOf(NotFoundException::class.java)
        `when`(controls.features(user, policy)).thenReturn(emptySet())
        assertThatThrownBy { backend.authorize("zip-code", "/run", "POST", user) }.isInstanceOf(ForbiddenException::class.java)
        user.active = false
        assertThatThrownBy { backend.authorize("zip-code", "/run", "POST", user) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(worker)
    }
    @Test fun `callback restores security context and uses real member identity for table checks`() {
        configure(setOf("data.read"))
        val old = UsernamePasswordAuthenticationToken("previous", "unused")
        SecurityContextHolder.getContext().authentication = old
        `when`(records.list("zip-code", "plugin_zip_code_notes", user, 0)).thenAnswer {
            assertThat(SecurityContextHolder.getContext().authentication.name).isEqualTo(user.email)
            assertThat(SecurityContextHolder.getContext().authentication.authorities.map { it.authority }).containsExactly("ROLE_USER")
            emptyList<PluginRecord>()
        }
        broker.call("ticket", PluginHostCall("data.list", json.readTree("""{"table":"plugin_zip_code_notes"}""")))
        assertThat(SecurityContextHolder.getContext().authentication).isSameAs(old)
        verify(records).list("zip-code", "plugin_zip_code_notes", user, 0)
    }
    @Test fun `ungranted writes foreign HTTP origins cookies and GET side effects are denied`() {
        configure(setOf("data.read", "http"), "GET")
        val inputs = listOf(
            "data.create" to """{"table":"plugin_zip_code_notes","data":{"title":"blocked"}}""",
            "http" to """{"url":"https://other.example.com/private"}""",
            "http" to """{"url":"https://example.com/api","method":"POST"}""",
            "http" to """{"url":"https://example.com/api","headers":{"Cookie":"session=value"}}"""
        )
        inputs.forEach { (operation, body) -> assertThatThrownBy { broker.call("ticket", PluginHostCall(operation, json.readTree(body))) }.isInstanceOfAny(ForbiddenException::class.java, BadRequestException::class.java) }
        verifyNoInteractions(records, http)
    }
    @Test fun `disabled plugin and stale callback cannot bypass lifecycle checks`() {
        configure(setOf("data.read"))
        doThrow(NotFoundException("disabled")).`when`(registry).requireAvailable("zip-code", "USER")
        assertThatThrownBy { broker.call("ticket", PluginHostCall("data.list", json.readTree("""{"table":"plugin_zip_code_notes"}"""))) }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(records, http)
    }
    @Test fun `only this plugins declared secret can be injected into an allowed HTTP request`() {
        configure(setOf("http"))
        `when`(config.current("zip-code")).thenReturn(mapOf("api-key" to "private-unit-canary"))
        val headers = mapOf("Authorization" to "Bearer private-unit-canary")
        `when`(http.scoped("POST", "https://example.com", "/jobs", headers, "{\"title\":\"x\"}")).thenReturn(IntegrationResponse(200, json.readTree("{\"ok\":true}")))
        val result = broker.call("ticket", PluginHostCall("http", json.readTree("""{"method":"POST","url":"https://example.com/jobs","body":{"title":"x"},"credential":{"key":"api-key"}}""")))
        assertThat(json.writeValueAsString(result)).contains("ok").doesNotContain("private-unit-canary")
        verify(http).scoped("POST", "https://example.com", "/jobs", headers, "{\"title\":\"x\"}")
        assertThatThrownBy { broker.call("ticket", PluginHostCall("http", json.readTree("""{"method":"POST","url":"https://example.com/jobs","credential":{"key":"foreign-key"}}"""))) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `worker receives only own artifacts public configuration and scoped actor metadata`() {
        configure()
        val descriptor = catalogue.descriptor(record)
        val stored = PluginPackageRecord("zip-code", filesJson = """{"backend/main.py":"code","frontend/app.js":"frontend"}""", sha256 = record.sha256)
        `when`(catalogue.find("zip-code")).thenReturn(stored)
        `when`(catalogue.descriptor(stored)).thenReturn(descriptor)
        val scope = InvocationScope("zip-code", user.id, record.sha256, "/run", "POST")
        `when`(tickets.create(scope)).thenReturn("private-invocation-ticket")
        `when`(config.view("zip-code")).thenReturn(PluginConfigurationView(mapOf("heading" to "Public"), setOf("api-key"), emptyList(), 0, true))
        doAnswer { call ->
            val payload = call.getArgument<Map<String,Any?>>(0)
            assertThat(payload["files"]).isEqualTo(mapOf("backend/main.py" to "code"))
            assertThat(json.writeValueAsString(payload["request"])).contains("Public", user.id.toString()).doesNotContain("api-key", "password", user.email)
            json.readTree("{\"status\":200,\"body\":{\"ok\":true}}")
        }.`when`(worker).invoke(anyMap())
        assertThat(backend.invoke("zip-code", "/run", "POST", json.readTree("{}"), emptyMap(), user).path("body").path("ok").asBoolean()).isTrue()
        verify(tickets).finish("private-invocation-ticket")
        verify(config, never()).current("zip-code")
    }
    @Test fun `plugin error responses are marked separately from application authentication failures`() {
        val service = mock(PluginBackendService::class.java)
        val actor = mock(PluginActor::class.java)
        val principal = org.springframework.security.core.userdetails.User.withUsername(user.email).password("unused").roles("USER").build()
        `when`(actor.current(user.email)).thenReturn(user)
        `when`(service.invoke("zip-code", "/run", "POST", null, emptyMap(), user)).thenReturn(json.readTree("{\"status\":401,\"body\":{\"error\":\"Plugin provider needs configuration\"}}"))
        val controller = PluginBackendController(service, broker, actor)
        val response = controller.invoke("zip-code", principal, org.springframework.mock.web.MockHttpServletRequest("POST", "/api/plugins/zip-code/backend/run"), null)
        assertThat(response.statusCode.value()).isEqualTo(401)
        assertThat(response.headers.getFirst("X-Meshable-Plugin-Response")).isEqualTo("1")
        assertThat(response.headers.cacheControl).isEqualTo("no-store")
    }
    @Test fun `shared settings callback requires permission and returns only the service public view`() {
        val call=PluginHostCall("settings.shared",json.readTree("""{"reference":"account"}"""))
        configure()
        assertThatThrownBy {broker.call("ticket",call)}.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(shared)
        configure(setOf("settings.read"))
        `when`(shared.publicCurrent("zip-code","account")).thenReturn(mapOf("origin" to "https://example.com"))
        assertThat(broker.call("ticket",call)).isEqualTo(mapOf("origin" to "https://example.com"))
        verify(config,never()).current("zip-code")
        verify(shared,never()).current("zip-code","account")
    }
    @Test fun `shared HTTP credentials are injected by the host without being given to worker configuration`() {
        configure(setOf("http"))
        `when`(shared.httpCredential("zip-code","account","token","https://example.com")).thenReturn("private-shared-canary")
        `when`(http.scoped("GET","https://example.com","/jobs",mapOf("Authorization" to "Bearer private-shared-canary"),null)).thenReturn(IntegrationResponse(200,json.readTree("{\"ok\":true}")))
        val result=broker.call("ticket",PluginHostCall("http",json.readTree("""{"url":"https://example.com/jobs","credential":{"sharedReference":"account","key":"token"}}""")))
        assertThat(json.writeValueAsString(result)).contains("ok").doesNotContain("private-shared-canary")
        verify(shared).httpCredential("zip-code","account","token","https://example.com")
        verify(config,never()).current("zip-code")
    }
}
