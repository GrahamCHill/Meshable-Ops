package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import com.starter.projects.ProjectReportInput
import com.starter.projects.ProjectReportKind
import com.starter.plugins.knowledge.KnowledgeExportInput
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDate

/** Only server-consumed approvals populate this attribute. No headers or query flags can supply it. */
object WorkflowExportOptions {
    val FIELDS = setOf("export.from","export.to","export.report.kind","export.report.title","export.report.includeDescriptions","export.report.includeAiSummary","export.knowledge.title","export.format")
    @Suppress("UNCHECKED_CAST")
    private fun current() = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        ?.getAttribute(WorkflowActionGate.EXPORT_OPTIONS) as? Map<String,String> ?: emptyMap()
    fun report(input: ProjectReportInput): ProjectReportInput = report(input,current())
    fun report(input: ProjectReportInput, options: Map<String,String>): ProjectReportInput = input.copy(
        from=date(options,"export.from",input.from),to=date(options,"export.to",input.to),
        kind=options["export.report.kind"]?.let { value -> ProjectReportKind.entries.find { it.name==value.uppercase() }
            ?: throw BadRequestException("Workflow report kind must be SPRINT, MONTHLY, or PERIOD") } ?: input.kind,
        title=title(options,"export.report.title",input.title),
        includeDescriptions=boolean(options,"export.report.includeDescriptions",input.includeDescriptions),
        includeAiSummary=boolean(options,"export.report.includeAiSummary",input.includeAiSummary))
    fun knowledge(input: KnowledgeExportInput) = input.copy(title=title(current(),"export.knowledge.title",input.title))
    fun format(original: String): String = current()["export.format"]?.let { value ->
        value.uppercase().takeIf { it in setOf("CSV","PDF") } ?: throw BadRequestException("Workflow export format must be CSV or PDF")
    } ?: original
    fun dates(from: LocalDate,to: LocalDate): Pair<LocalDate,LocalDate> = current().let { date(it,"export.from",from) to date(it,"export.to",to) }
    private fun date(options: Map<String,String>,key: String,original: LocalDate) = options[key]?.let {
        runCatching {LocalDate.parse(it)}.getOrElse {throw BadRequestException("Workflow $key must be an ISO date")}
    } ?: original
    private fun title(options: Map<String,String>,key: String,original: String) = options[key]?.let {
        if(it.isBlank() || it.length>200 || it.any { char -> char.isISOControl() }) throw BadRequestException("Workflow export titles must contain 1–200 printable characters")
        it.trim()
    } ?: original
    private fun boolean(options: Map<String,String>,key: String,original: Boolean) = options[key]?.let {
        when(it.lowercase()) {"true"->true;"false"->false;else->throw BadRequestException("Workflow $key must be true or false")}
    } ?: original
}
