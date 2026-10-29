package com.starter.plugins.workflows

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ContentCachingRequestWrapper

class WorkflowActionGateTest {
    private val bindings=mock(WorkflowActionBindingRepository::class.java)
    private val catalogue=WorkflowActionCatalogue(mock(org.springframework.beans.factory.ObjectProvider::class.java) as org.springframework.beans.factory.ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping>)
    private val store=mock(WorkflowActionStore::class.java)
    private val binding=WorkflowActionBinding(selector="category:external")
    private val row=WorkflowActionRequest(bindingId=binding.id)
    private val gate=WorkflowActionGate(bindings,catalogue,emptyList(),store)
    @BeforeEach fun authenticate() { SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken("member@test.local","",emptyList()) }
    @AfterEach fun cleanup() { SecurityContextHolder.clearContext() }
    private fun request(body: String="{\"recipient\":\"a@test.local\"}"): ContentCachingRequestWrapper {
        val source=MockHttpServletRequest("POST","/api/documents/123/email")
        source.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/documents/{id}/email")
        source.setContent(body.toByteArray());source.contentType="application/json"
        return ContentCachingRequestWrapper(source).also { it.inputStream.readAllBytes() }
    }
    @Test fun `a pending workflow blocks the action and only a completed one is consumed once per request`() {
        `when`(bindings.findByEnabledTrueOrderById()).thenReturn(listOf(binding))
        `when`(store.prepare(anyString(),eq(binding.id) ?: binding.id,anyString(),anyMap())).thenReturn(row)
        assertThatThrownBy {gate.enforce(request())}.isInstanceOf(WorkflowRequiredException::class.java)
        verify(store,never()).consume(anyString(),anyList(),anyString())
        `when`(store.completed("member@test.local",row)).thenReturn(true)
        val retry=request()
        gate.enforce(retry)
        gate.enforce(retry,external=true)
        verify(store,times(1)).consume(eq("member@test.local") ?: "member@test.local",eq(listOf(row)) ?: listOf(row),anyString())
    }
    @Test fun `fingerprints cover exact JSON passwords paths methods and repeated query values`() {
        assertThat(WorkflowActionGate.fingerprint(request("{\"password\":\"first\"}")))
            .isNotEqualTo(WorkflowActionGate.fingerprint(request("{\"password\":\"second\"}")))
        val one=request();val two=request();(two.request as MockHttpServletRequest).addHeader("X-Workflow-Approved","true")
        assertThat(WorkflowActionGate.fingerprint(one)).isEqualTo(WorkflowActionGate.fingerprint(two))
        (two.request as MockHttpServletRequest).addParameter("recipient","other@test.local")
        assertThat(WorkflowActionGate.fingerprint(one)).isNotEqualTo(WorkflowActionGate.fingerprint(two))
    }
    @Test fun `plugin groups govern mutations while workflow control and authentication cannot deadlock themselves`() {
        assertThat(catalogue.eligible("/api/auth/mfa/verify")).isFalse()
        assertThat(catalogue.eligible("/api/workflows/runs/123/advance")).isFalse()
        assertThat(catalogue.eligible("/api/workflows/runs/123/email")).isTrue()
        assertThat(catalogue.eligible("/api/admin/workflows/bindings")).isFalse()
        assertThat(catalogue.external("/api/plugins/billing/123/send")).isTrue()
    }
    @Test fun `unrelated public requests are unaffected and manual stream bindings require a payload adapter`() {
        `when`(bindings.findByEnabledTrueOrderById()).thenReturn(listOf(binding))
        SecurityContextHolder.clearContext()
        gate.enforce(MockHttpServletRequest("GET","/api/projects"))
        authenticate()
        val streamed=request().apply {setAttribute(WorkflowActionGate.STREAM_ADAPTER_REQUIRED,true)}
        assertThatThrownBy {gate.enforce(streamed)}.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        verifyNoInteractions(store)
    }
    @Test fun `reviewed plugins can register their own logical action without core matcher changes`() {
        val custom=object : WorkflowActionContextProvider {
            override val actions=listOf(WorkflowActionDescriptor("example.approve","Approve record","example"))
            override fun supports(path: String)=path.startsWith("/api/example/")
            override fun context(email: String,path: String,body: Any?)=WorkflowTriggerContext(setOf("example.approve"),mapOf("trigger.record" to "42"))
        }
        val policy=WorkflowActionBinding(selector="example.approve")
        `when`(bindings.findByEnabledTrueOrderById()).thenReturn(listOf(policy))
        `when`(store.prepare(anyString(),eq(policy.id) ?: policy.id,anyString(),anyMap())).thenReturn(row)
        assertThatThrownBy { WorkflowActionGate(bindings,catalogue,listOf(custom),store).enforce(MockHttpServletRequest("POST","/api/example/42")) }
            .isInstanceOf(WorkflowRequiredException::class.java)
    }
    @Test fun `encoded URL and matrix parameter aliases cannot bypass export policies`() {
        `when`(bindings.findByEnabledTrueOrderById()).thenReturn(listOf(binding))
        `when`(store.prepare(anyString(),eq(binding.id) ?: binding.id,anyString(),anyMap())).thenReturn(row)
        for(path in listOf("/api/plugins/billing/123/p%64f","/api/plugins/billing/123/pdf;download=true"))
            assertThatThrownBy {gate.enforce(MockHttpServletRequest("GET",path))}.isInstanceOf(WorkflowRequiredException::class.java)
    }
    @Test fun `scalar scopes match complete comma-bearing values and oversized context fails closed`() {
        val provider=object : WorkflowActionContextProvider {
            var title="Budget, final"
            override fun supports(path: String)=true
            override fun context(email: String,path: String,body: Any?)=WorkflowTriggerContext(values=mapOf("trigger.input.title" to title))
        }
        val scoped=WorkflowActionBinding(selector="category:external",contextField="trigger.input.title",contextEquals="Budget, final")
        `when`(bindings.findByEnabledTrueOrderById()).thenReturn(listOf(scoped))
        `when`(store.prepare(anyString(),eq(scoped.id) ?: scoped.id,anyString(),anyMap())).thenReturn(row)
        val scopedGate=WorkflowActionGate(bindings,catalogue,listOf(provider),store)
        assertThatThrownBy {scopedGate.enforce(request())}.isInstanceOf(WorkflowRequiredException::class.java)
        provider.title="x".repeat(501);scoped.contextField=""
        assertThatThrownBy {scopedGate.enforce(request())}.isInstanceOf(com.starter.exception.BadRequestException::class.java)
        verify(store,times(1)).prepare(anyString(),eq(scoped.id) ?: scoped.id,anyString(),anyMap())
    }
}
