package com.starter.plugins.workflows

import com.starter.exception.GlobalExceptionHandler
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID

class WorkflowActionWebTest {
    @RestController class Actions {
        var sent=0
        @PostMapping("/api/test/send") fun send(@RequestBody body: Map<String,String>): Map<String,String> { sent++;return body }
        @GetMapping("/api/test/download") fun download(): String { sent++;return "file" }
        @PostMapping("/api/test/optional") fun optional(@RequestBody(required=false) body: Map<String,String>?): String { sent++;return "ok" }
        @GetMapping("/api/plugins/google-analytics/contexts/PROJECT/{id}/links/export/{format}") fun export(@PathVariable format: String): Map<String,String> {sent++;return mapOf("format" to WorkflowExportOptions.format(format))}
    }
    @Test fun `JSON body bodyless downloads and empty optional bodies are intercepted before execution`() {
        val controller=Actions()
        val gate=mock(WorkflowActionGate::class.java)
        val detail=WorkflowActionRequired("Approval required",workflowRequestId=UUID.randomUUID(),runId=UUID.randomUUID(),workflowName="Review")
        doThrow(WorkflowRequiredException(detail)).`when`(gate).enforce(any(HttpServletRequest::class.java) ?: MockHttpServletRequest(),any(),anyBoolean())
        val mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler(),WorkflowActionBodyAdvice(gate))
            .addInterceptors(WorkflowActionInterceptor(gate)).addFilter<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(WorkflowRequestCapture()).build()
        mvc.perform(post("/api/test/send").contentType(MediaType.APPLICATION_JSON).content("""{"recipient":"test@example.test"}"""))
            .andExpect(status().isPreconditionRequired).andExpect(jsonPath("$.runId").value(detail.runId.toString()))
        mvc.perform(get("/api/test/download")).andExpect(status().isPreconditionRequired)
        mvc.perform(post("/api/test/optional").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isPreconditionRequired)
        assertThat(controller.sent).isZero()
    }
    @Test fun `logical plugin exports block before execution and receive approved options only on the final retry`() {
        val controller=Actions();val bindings=mock(WorkflowActionBindingRepository::class.java);val store=mock(WorkflowActionStore::class.java)
        @Suppress("UNCHECKED_CAST")
        val catalogue=WorkflowActionCatalogue(mock(org.springframework.beans.factory.ObjectProvider::class.java) as org.springframework.beans.factory.ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping>)
        val binding=WorkflowActionBinding(selector="google-analytics.export");val approval=WorkflowActionRequest(bindingId=binding.id)
        `when`(bindings.findByEnabledTrueOrderById()).thenReturn(listOf(binding))
        `when`(store.prepare(anyString(),eq(binding.id) ?: binding.id,anyString(),anyMap())).thenReturn(approval)
        `when`(store.consume(anyString(),anyList(),anyString())).thenReturn(mapOf("export.format" to "PDF"))
        val gate=WorkflowActionGate(bindings,catalogue,listOf(PluginWorkflowContext()),store)
        val mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler(),WorkflowActionBodyAdvice(gate))
            .addInterceptors(WorkflowActionInterceptor(gate)).build()
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication=org.springframework.security.authentication.UsernamePasswordAuthenticationToken("member@example.test","",emptyList())
        try {
            val path="/api/plugins/google-analytics/contexts/PROJECT/project/links/export/csv"
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isPreconditionRequired)
            assertThat(controller.sent).isZero()
            verify(store,never()).consume(anyString(),anyList(),anyString())
            `when`(store.completed("member@example.test",approval)).thenReturn(true)
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk).andExpect(jsonPath("$.format").value("PDF"))
            assertThat(controller.sent).isEqualTo(1)
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext()}
    }
}
