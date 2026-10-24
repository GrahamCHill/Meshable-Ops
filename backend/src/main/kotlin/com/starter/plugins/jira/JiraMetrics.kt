package com.starter.plugins.jira

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate

data class JiraMetrics(val total: Int = 0, val completed: Int = 0, val inProgress: Int = 0, val todo: Int = 0,
    val overdue: Int = 0, val storyPoints: Double? = null, val statuses: Map<String,Int> = emptyMap(),
    val types: Map<String,Int> = emptyMap(), val completedLast30Days: Int = 0, val partial: Boolean = false, val syncedAt: Instant = Instant.now())

object JiraMetricCalculator {
    fun calculate(issues: List<JsonNode>, pointsField: String?, partial: Boolean, today: LocalDate = LocalDate.now(java.time.ZoneOffset.UTC)): JiraMetrics {
        val unique = issues.distinctBy { it.path("id").asText(it.path("key").asText()) }
        fun fields(issue: JsonNode) = issue.path("fields")
        fun category(issue: JsonNode) = fields(issue).path("status").path("statusCategory").path("key").asText()
        val completed = unique.count { category(it) == "done" }
        val progress = unique.count { category(it) == "indeterminate" }
        return JiraMetrics(unique.size,completed,progress,unique.size-completed-progress,
            unique.count { category(it) != "done" && runCatching { LocalDate.parse(fields(it).path("duedate").asText()).isBefore(today) }.getOrDefault(false) },
            pointsField?.let { field -> unique.sumOf { fields(it).path(field).takeIf(JsonNode::isNumber)?.asDouble()?.takeIf(Double::isFinite) ?: 0.0 } },
            unique.groupingBy { fields(it).path("status").path("name").asText("Unknown").take(100) }.eachCount(),
            unique.groupingBy { fields(it).path("issuetype").path("name").asText("Unknown").take(100) }.eachCount(),
            unique.count { category(it) == "done" && runCatching { LocalDate.parse(fields(it).path("resolutiondate").asText().take(10)) >= today.minusDays(30) }.getOrDefault(false) },partial)
    }
}
