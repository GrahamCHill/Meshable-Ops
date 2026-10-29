package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import com.starter.projects.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.*
import java.time.LocalDate
import java.util.UUID

class WorkflowExportOptionsTest {
    private val original=ProjectReportInput(from=LocalDate.parse("2026-10-01"),to=LocalDate.parse("2026-10-29"),sendEmail=true,recipientIds=setOf(UUID.randomUUID()))
    @AfterEach fun cleanup() {RequestContextHolder.resetRequestAttributes()}
    @Test fun `approved outputs change only supported report content without changing recipients or board scope`() {
        val result=WorkflowExportOptions.report(original,mapOf("export.from" to "2026-10-02","export.report.title" to "Reviewed report",
            "export.report.includeDescriptions" to "False","export.report.includeAiSummary" to "False","export.recipientIds" to "attacker"))
        assertThat(result.from).isEqualTo(LocalDate.parse("2026-10-02"));assertThat(result.title).isEqualTo("Reviewed report")
        assertThat(result.includeDescriptions).isFalse();assertThat(result.includeAiSummary).isFalse()
        assertThat(result.recipientIds).isEqualTo(original.recipientIds);assertThat(result.sendEmail).isTrue();assertThat(result.boardId).isEqualTo(original.boardId)
        for(options in listOf(mapOf("export.from" to "yesterday"),mapOf("export.report.includeDescriptions" to "yes"),mapOf("export.report.title" to "x\nmalformed"),mapOf("export.report.kind" to "invalid")))
            assertThatThrownBy {WorkflowExportOptions.report(original,options)}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `client headers and parameters cannot inject options and format changes follow consumed server outputs`() {
        val request=MockHttpServletRequest().apply {addHeader("X-Workflow-Export-Format","PDF");addParameter("export.format","PDF")}
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        assertThat(WorkflowExportOptions.format("CSV")).isEqualTo("CSV")
        request.setAttribute(WorkflowActionGate.EXPORT_OPTIONS,mapOf("export.format" to "PDF"))
        assertThat(WorkflowExportOptions.format("CSV")).isEqualTo("PDF")
        request.setAttribute(WorkflowActionGate.EXPORT_OPTIONS,mapOf("export.format" to "HTML"))
        assertThatThrownBy {WorkflowExportOptions.format("CSV")}.isInstanceOf(BadRequestException::class.java)
    }
}
