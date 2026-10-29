package com.starter.plugins.workflows

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.*
import org.springframework.web.servlet.HandlerMapping

class PluginWorkflowContextTest {
    private val provider=PluginWorkflowContext()
    @AfterEach fun cleanup() { RequestContextHolder.resetRequestAttributes() }
    @Test fun `report generation and each export have method-specific plugin contracts`() {
        val samples=mapOf(
            "projects.report.generate" to ("POST" to "/api/projects/project/reports"),
            "projects.report.export" to ("GET" to "/api/projects/project/reports/report/pdf"),
            "billing.export" to ("GET" to "/api/plugins/billing/bill/pdf"),
            "knowledge.export" to ("POST" to "/api/plugins/knowledge/exports"),
            "timesheets.export" to ("GET" to "/api/plugins/timesheets/export"),
            "google-analytics.export" to ("GET" to "/api/plugins/google-analytics/contexts/PROJECT/project/links/export/csv"))
        samples.forEach { (id,pair) ->
            val (method,path)=pair
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest(method,path)))
            assertThat(provider.context("member@example.test",path,null).selectors).containsExactly(id)
            assertThat(provider.actionsFor(method,path).single().external).isTrue()
            assertThat(provider.actionsFor("OPTIONS",path)).isEmpty()
        }
        assertThat(provider.actionsFor("GET","/api/projects/project/reports")).isEmpty()
        assertThat(provider.supports("/api/plugins/knowledge/exports/prepare")).isFalse()
        assertThat(provider.actionsFor("POST","/api/plugins/knowledge/pages").single().external).isFalse()
    }
    @Test fun `query and typed body context exposes scoped dates and action while excluding secrets`() {
        val request=MockHttpServletRequest("GET","/api/plugins/timesheets/export").apply {
            addParameter("from","2026-10-01");addParameter("to","2026-10-29");addParameter("password","secret")
            setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,mapOf("projectId" to "project"))
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        val values=BusinessWorkflowContext(jacksonObjectMapper()).context("member@example.test",request.requestURI.orEmpty(),mapOf("action" to "APPROVE","token" to "secret")).values
        assertThat(values).containsEntry("trigger.input.from","2026-10-01").containsEntry("trigger.input.action","APPROVE").containsEntry("trigger.resource.projectId","project")
        assertThat(values.keys).noneMatch { it.contains("password") || it.contains("token") }
    }
}
