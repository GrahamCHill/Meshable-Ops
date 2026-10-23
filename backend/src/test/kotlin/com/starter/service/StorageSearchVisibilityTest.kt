package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestTemplate

class StorageSearchVisibilityTest {
    @Test
    fun `folder and document search both check current sharing state instead of cached vector payloads`() {
        val statements = mutableListOf<String>()
        val jdbc = mock(JdbcTemplate::class.java) { invocation ->
            if (invocation.method.name == "query") {
                statements += invocation.arguments[0] as String
                emptyList<VectorSearchPoint>()
            } else Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        VectorService(jdbc, jacksonObjectMapper(), mock(RestTemplate::class.java)).search(listOf(1f, 0f), 10)
        val sql = statements.single()
        assertThat(sql).contains("JOIN items i ON i.id = iv.item_id", "JOIN items i ON i.id = d.item_id")
        assertThat(Regex("i.accessible = TRUE AND \\(sc.id IS NULL OR sc.enabled = TRUE\\)").findAll(sql).count()).isEqualTo(2)
    }
}
