package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import com.starter.service.AuditLogService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class WorkflowDiagramBindingsTest {
    private val bindings=mock(WorkflowActionBindingRepository::class.java)
    private val catalogue=mock(WorkflowActionCatalogue::class.java)
    private val service=WorkflowDiagramBindings(bindings,catalogue,mock(AuditLogService::class.java))
    @Test fun `publishing connects a diagram trigger while drafts retain the enforced policy and explicit published removal disables it`() {
        val definition=WorkflowDefinition(state=WorkflowState.READY,published=true)
        val start=WorkflowBlock("start",BlockKind.START,"Export report",actionSelector="projects.report.export")
        val graph=WorkflowGraph(listOf(start),emptyList())
        `when`(catalogue.all()).thenReturn(listOf(WorkflowActionDescriptor(start.actionSelector,"Report export","projects")))
        `when`(bindings.saveAndFlush(any(WorkflowActionBinding::class.java))).thenAnswer {it.arguments[0]}
        service.sync("admin@example.test",definition,graph)
        val capture=org.mockito.ArgumentCaptor.forClass(WorkflowActionBinding::class.java)
        verify(bindings).saveAndFlush(capture.capture());val row=capture.value
        assertThat(row.diagramWorkflowId).isEqualTo(definition.id);assertThat(row.selector).isEqualTo(start.actionSelector)
        `when`(bindings.findByDiagramWorkflowId(definition.id)).thenReturn(row)
        definition.published=false
        service.sync("admin@example.test",definition,graph.copy(nodes=listOf(start.copy(actionSelector=""))))
        assertThat(row.enabled).isTrue()
        definition.published=true
        service.sync("admin@example.test",definition,graph.copy(nodes=listOf(start.copy(actionSelector=""))))
        assertThat(row.enabled).isFalse()
        assertThatThrownBy {service.sync("admin@example.test",definition,graph.copy(nodes=listOf(start.copy(actionSelector="removed.plugin"))))}.isInstanceOf(BadRequestException::class.java)
    }
}
