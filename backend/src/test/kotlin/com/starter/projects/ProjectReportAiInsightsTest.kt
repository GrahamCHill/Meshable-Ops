package com.starter.projects

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.ai.*
import com.starter.plugins.kanban.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate
import java.util.UUID

class ProjectReportAiInsightsTest {
    private val reports=mock(ProjectReportCoverService::class.java)
    private val settings=mock(AiSettingsService::class.java)
    private val evidence=mutableListOf<String>()
    private var unavailable=false
    private val provider=mock(AiProviderClient::class.java) {call->
        if(call.method.name=="answer") {
            evidence+=call.getArgument<String>(2)
            if(unavailable)throw IllegalStateException("Unavailable")
            "Delivery is progressing. Review the blocker."
        } else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val endpoint=AiEndpoint(AiProvider.OPENAI_COMPATIBLE,"https://example.test","test-model")
    private val input=ProjectReportInput(from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,31))
    private val service=ProjectReportAiInsights(reports,settings,provider,jacksonObjectMapper().findAndRegisterModules())
    @Test fun `report AI is off unless enabled by an administrator`() {
        assertThat(service.summarize("Delivery",input,emptyList())).isNull()
        verifyNoInteractions(settings,provider)
    }
    @Test fun `enabled insights summarize only the supplied project facts`() {
        `when`(reports.aiInsightsEnabled()).thenReturn(true)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(true,false,endpoint,endpoint))
        val items=listOf(ProjectWorkItem(UUID.randomUUID(),"Delivery board",KanbanCard(UUID.randomUUID(),UUID.randomUUID(),"Visible project ticket",blocked=true)))
        assertThat(service.summarize("Delivery",input,items)).isEqualTo("Delivery is progressing. Review the blocker.")
        assertThat(evidence.single()).contains("Visible project ticket","Delivery").doesNotContain("calendar","riskScore")
    }
    @Test fun `a provider failure leaves the ordinary report available`() {
        `when`(reports.aiInsightsEnabled()).thenReturn(true)
        `when`(settings.current()).thenReturn(ActiveAiConfiguration(true,false,endpoint,endpoint))
        unavailable=true
        assertThat(service.summarize("Delivery",input,emptyList())).isNull()
    }
}
